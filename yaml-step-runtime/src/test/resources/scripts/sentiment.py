#!/usr/bin/env python3
import json, sys
data = json.load(sys.stdin)
print(json.dumps({"score": 0.8, "label": "positive"}))
