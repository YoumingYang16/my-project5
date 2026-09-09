# Shared communication brief

The image workflow and a future copywriting workflow exchange a versioned `communicationBrief` object. Neither module needs to know the internal implementation of the other.

## Stable input

```json
{
  "briefVersion": "1.0",
  "paperType": "digital-heritage",
  "audience": "general-public",
  "communicationGoal": "show-application",
  "coreMessage": "The paper documents a historic building with scanning and HBIM reconstruction.",
  "coverHeadline": "古建筑如何被数字化保存？",
  "evidence": [],
  "limitations": [],
  "mustAvoidClaims": ["first-ever", "completely solves", "guaranteed"],
  "visual": {
    "mainSubject": "historic timber building",
    "environment": "heritage documentation site",
    "action": "laser scanning and reconstruction",
    "technology": "terrestrial laser scanner and HBIM",
    "supportingObjects": ["survey tripod"],
    "forbiddenObjects": ["VR headset", "robot", "modern office"],
    "uniqueAnchors": ["timber joints", "laser scan", "HBIM model"],
    "peoplePolicy": "optional",
    "handsRequired": false
  }
}
```

The image module consumes `visual` plus the shared evidence and audience fields. A copywriting module should consume the shared fields, evidence, limitations, and claim restrictions. Website adapters remain responsible only for reading paper data and writing the selected asset path.

## Compatibility

- Existing webhook requests without `communicationBrief` continue to work.
- Existing BibTeX `preview` fields and al-folio paths do not change.
- New website adapters can send the same JSON fields without copying al-folio-specific code.
- Provider-specific details stay behind `imageProviderMode`, so Qwen, ComfyUI, or a future API can be exchanged without changing the content brief.
