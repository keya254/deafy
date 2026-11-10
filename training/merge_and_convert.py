#!/usr/bin/env python3
"""Merge a Whisper LoRA adapter into its base model and save it in OpenAI Whisper format.

sherpa-onnx's exporter (scripts/whisper/export-onnx.py in the sherpa-onnx repo) loads models with
openai-whisper, which needs an OpenAI-format checkpoint (a .pt file with "dims" and
"model_state_dict"). Hugging Face LoRA adapters are not in that format, so this script:

  1. loads the base model (openai/whisper-small by default) and applies the adapter,
  2. merges the LoRA weights into the base weights,
  3. renames every tensor to the OpenAI layout and writes the checkpoint.

Then export the checkpoint to ONNX with the sherpa-onnx exporter. See docs/TRAINING.md.

Usage:
  python3 training/merge_and_convert.py --adapter eval/models/keya254-lora \
      --out eval/models/whisper-small-sw-en.pt
"""

import argparse
import json
import re
import sys
from pathlib import Path

import torch
from peft import PeftModel
from transformers import WhisperForConditionalGeneration

# Hugging Face name -> OpenAI name, for the parts that are the same in both layouts.
TOP_LEVEL = {
    "model.encoder.conv1": "encoder.conv1",
    "model.encoder.conv2": "encoder.conv2",
    "model.encoder.embed_positions": "encoder.positional_embedding",
    "model.encoder.layer_norm": "encoder.ln_post",
    "model.decoder.embed_tokens": "decoder.token_embedding",
    "model.decoder.embed_positions": "decoder.positional_embedding",
    "model.decoder.layer_norm": "decoder.ln",
}

ATTN_NAMES = {"q_proj": "query", "k_proj": "key", "v_proj": "value", "out_proj": "out"}

# Positional embeddings are bare tensors in the OpenAI layout (no ".weight" suffix).
EXACT = {
    "model.encoder.embed_positions.weight": "encoder.positional_embedding",
    "model.decoder.embed_positions.weight": "decoder.positional_embedding",
}


def convert_name(hf_name: str) -> str | None:
    """Return the OpenAI name for a Hugging Face weight name, or None to drop it."""
    if hf_name in EXACT:
        return EXACT[hf_name]
    for hf_prefix, openai_prefix in TOP_LEVEL.items():
        if hf_name.startswith(hf_prefix + "."):
            return openai_prefix + hf_name[len(hf_prefix):]
    if hf_name == "proj_out.weight":
        return None
    m = re.match(r"model\.(encoder|decoder)\.layers\.(\d+)\.(.+)\.(weight|bias)$", hf_name)
    if not m:
        return None
    side, idx, module, kind = m.group(1), m.group(2), m.group(3), m.group(4)
    block = f"{side}.blocks.{idx}"
    attn = re.match(r"(self_attn|encoder_attn)\.(q_proj|k_proj|v_proj|out_proj)$", module)
    if attn:
        where = "attn" if attn.group(1) == "self_attn" else "cross_attn"
        return f"{block}.{where}.{ATTN_NAMES[attn.group(2)]}.{kind}"
    layer_norms = {
        "self_attn_layer_norm": "attn_ln",
        "encoder_attn_layer_norm": "cross_attn_ln",
        "final_layer_norm": "mlp_ln",
    }
    if module in layer_norms:
        return f"{block}.{layer_norms[module]}.{kind}"
    mlp = {"fc1": "mlp.0", "fc2": "mlp.2"}
    if module in mlp:
        return f"{block}.{mlp[module]}.{kind}"
    return None


def dims_from_config(config) -> dict:
    return {
        "n_mels": config.num_mel_bins,
        "n_audio_ctx": config.max_source_positions,
        "n_audio_state": config.d_model,
        "n_audio_head": config.encoder_attention_heads,
        "n_audio_layer": config.encoder_layers,
        "n_vocab": config.vocab_size,
        "n_text_ctx": config.max_target_positions,
        "n_text_state": config.d_model,
        "n_text_head": config.decoder_attention_heads,
        "n_text_layer": config.decoder_layers,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", default="openai/whisper-small", help="base model (Hugging Face id)")
    parser.add_argument("--adapter", required=True, type=Path, help="folder with adapter_config.json and adapter_model.safetensors")
    parser.add_argument("--out", required=True, type=Path, help="OpenAI-format checkpoint to write (.pt)")
    args = parser.parse_args()

    adapter_config = json.loads((args.adapter / "adapter_config.json").read_text())
    print(f"Adapter: {adapter_config.get('peft_type')} r={adapter_config.get('r')} base={adapter_config.get('base_model_name_or_path')}")

    base = WhisperForConditionalGeneration.from_pretrained(args.base, torch_dtype=torch.float32)
    model = PeftModel.from_pretrained(base, str(args.adapter))
    model = model.merge_and_unload()
    model.eval()

    state = model.state_dict()
    openai_state = {}
    skipped = []
    for hf_name, tensor in state.items():
        new_name = convert_name(hf_name)
        if new_name is None:
            skipped.append(hf_name)
            continue
        openai_state[new_name] = tensor.detach().clone().contiguous()

    print(f"Converted {len(openai_state)} tensors. Dropped {len(skipped)} (for example: {skipped[:3]})")

    checkpoint = {"dims": dims_from_config(model.config), "model_state_dict": openai_state}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    torch.save(checkpoint, args.out)
    print(f"Wrote {args.out} ({args.out.stat().st_size / 1e6:.0f} MB). dims={checkpoint['dims']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
