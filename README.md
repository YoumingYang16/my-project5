# PaperLens

PaperLens is an evidence-aware system for turning academic papers into reviewable public-facing content. It accepts a PDF, extracts text, metadata, references, captions, and source figures, builds a grounded understanding of the paper, generates social copy and visual candidates through configurable AI providers, and keeps a human reviewer in control of the final selection.

The repository is more than a model-calling demo. It combines a Java 21 and Spring Boot application, a browser client, an n8n orchestration workflow, a Node.js rendering service, local or hosted image-generation backends, persistent data, security controls, and a broad regression suite.

## Why this project is technically interesting

- Evidence is collected before generation. PDF text, structured metadata, DOI enrichment, figure captions, and source images are assembled into a bounded evidence packet.
- AI providers are replaceable and failure-aware. The paper-understanding chain can route through n8n and DeepSeek before using a deterministic evidence fallback.
- Generated copy is checked against retrieved evidence instead of being accepted directly from a model.
- Image generation is multi-source. A paper can produce candidates from extracted figures, Qwen Image, and an optional local ComfyUI/IP-Adapter workflow.
- Rendering and evaluation are separate concerns. A dedicated renderer service performs composition, OCR, lightweight vision analysis, quality checks, and output normalization.
- Human review remains part of the workflow. Users can compare candidates and edit crop, containment, smart-crop, or stretch settings before choosing an asset.
- Secrets and research files are intentionally excluded from version control.

## End-to-end architecture

```text
Browser client
  |
  v
Spring Boot website
  |-- authentication, publications, settings, uploads, and review APIs
  |-- PDFBox text and figure extraction
  |-- GROBID metadata/full-text integration
  |-- DOI and reference grounding
  |-- evidence packet construction
  |-- paper-understanding provider chain
  |     n8n -> DeepSeek -> deterministic evidence fallback
  |-- evidence retrieval, social-copy generation, and claim validation
  |
  +------ short-lived, single-use task token ------> n8n workflow
  |
  +------ paper evidence and image options --------> renderer-api
                                                       |-- source-figure candidates
                                                       |-- Qwen Image
                                                       |-- optional ComfyUI/IP-Adapter
                                                       |-- OCR and local vision checks
                                                       |-- Satori/Sharp composition
                                                       v
                                                local candidate assets
  |
  v
Human comparison, editing, and final selection
```

## Repository components

| Path | Responsibility |
| --- | --- |
| `website/` | Java 21 Spring Boot application, static browser client, persistence, security, PDF analysis, AI orchestration, and tests |
| `image-workflow/renderer-api/` | Express service for image generation, rendering, OCR, local vision checks, candidate scoring, and image transformations |
| `image-workflow/n8n-workflow.json` | Importable n8n workflow for the paper-understanding stage |
| `image-workflow/nginx/` | Read-only asset-serving configuration |
| `scripts/` | Windows setup, startup, shutdown, workflow import, and experiment-data export helpers |
| `docs/` | Architecture, installation, deployment, security, visual-profile, and validation notes |
| `docker-compose.yml` | Local multi-service topology for the website, n8n, renderer, asset server, and persistent volumes |

## Evidence-first paper understanding

The central engineering problem is not simply obtaining fluent output. PaperLens must preserve the relationship between generated content and the source paper.

### Evidence packet construction

`PaperEvidencePacketBuilder` combines available evidence into a bounded representation. Depending on the paper and configured services, this can include:

- PDF text extracted with Apache PDFBox;
- title, abstract, DOI, authorship, and bibliographic metadata;
- GROBID TEI structure and full-text sections;
- DOI and reference enrichment;
- problem, method, implementation, evaluation, and conclusion snippets;
- figure captions and source-image candidates;
- evidence-source labels and warnings for missing inputs.

The builder applies character limits and records degraded inputs rather than silently treating partial extraction as complete evidence.

### Provider routing and graceful degradation

`PaperUnderstandingProviderChain` normalizes a configurable provider priority and attempts the available implementations in order. The default configuration supports:

1. n8n-orchestrated understanding;
2. direct DeepSeek understanding;
3. a deterministic evidence-based fallback.

Non-fallback model output passes through semantic validation before it becomes the resolved paper understanding. Provider failures and missing evidence are surfaced as warnings, so a single unavailable service does not have to invalidate the entire publication record.

### Grounded social-copy pipeline

The copy workflow separates planning, generation, and validation:

1. build or reuse the resolved paper understanding;
2. convert it into a content specification and evidence digest;
3. retrieve evidence spans relevant to the requested communication angle;
4. route generation through the configured social-copy provider;
5. verify individual claims against the evidence;
6. run final structural and quality validation;
7. return the selected copy together with diagnostics and pipeline artifacts.

This structure makes failure causes inspectable and supports deterministic fallback when a remote text provider is unavailable.

## Image candidate pipeline

PaperLens treats image generation as a candidate-ranking problem rather than a single opaque request.

### Candidate sources

- figures extracted from the uploaded paper;
- Qwen Image through DashScope;
- optional local ComfyUI generation;
- optional RealVisXL and IP-Adapter conditioning;
- deterministic or template-based fallbacks when external generation is unavailable.

### Renderer responsibilities

The Node.js renderer uses Express, Satori, Sharp, PDF.js, Tesseract.js, and an optional local Transformers.js vision model. Its responsibilities include:

- producing branded and ratio-specific compositions;
- normalizing local asset paths and generated outputs;
- extracting OCR text for basic legibility checks;
- computing candidate diagnostics and lightweight relevance signals;
- avoiding duplicate or low-quality variants;
- supporting source-image preservation and derived image sizes;
- returning local, persistent candidate references rather than temporary provider URLs.

### Human editing modes

The browser supports four editing behaviors:

- `cover`: crop to fill the requested frame;
- `contain`: preserve the full image and add background space;
- `smart`: derive a crop from the selected focus;
- `stretch`: resize through edge and corner controls.

Preset outputs include 3:2, 3:4, 1:1, and 16:9 formats.

## Application and data model

The Spring Boot service builds on an earlier publication platform and adds the paper-understanding and visual-generation workflows. Its application layer includes:

- JWT-based authentication and Spring Security request filtering;
- user profiles, password changes, and recovery questions;
- contributor applications and administrator review;
- publication drafting, moderation, archiving, comments, likes, and categories;
- PDF upload, metadata extraction, source-figure extraction, and DOI lookup;
- per-user AI provider settings;
- paper-understanding caches and generation diagnostics;
- H2 for local use and MySQL configuration for longer-lived deployments.

The repository snapshot contains 250 Java source and test files, including 17 controller classes and 47 service classes. The test tree contains 46 test classes and 151 `@Test` methods covering authentication, publication flows, provider routing, evidence construction, caching, validation, candidate ranking, rendering compatibility, and upload-path behavior.

## Security and privacy design

### Secret handling

- Setup creates a local `.env` with independent random JWT, n8n, and AI-settings encryption secrets.
- Real provider keys are not included in the repository.
- User AI settings are encrypted with AES-GCM when `AI_KEY_ENCRYPTION_SECRET` is configured.
- Full API keys are not returned to the browser after storage.
- n8n receives a high-entropy, short-lived, single-use task token rather than a stored user DeepSeek key.
- The Qwen key is passed for the current user and request; it is not written into image manifests.

### Data that must remain local

The `.gitignore` excludes `.env` files, private keys, databases, PDFs, uploads, generated assets, logs, Docker data, model caches, and archive files. Do not override these rules when publishing a fork.

Research papers and extracted figures may be copyrighted or confidential. Generated copy and images should receive factual, copyright, and safety review before publication.

### Deployment boundary

The default Compose file binds n8n, the renderer, and the asset server to loopback ports. A production deployment should additionally use HTTPS, persistent database management, object storage, access control, rate limiting, backups, and execution-record retention policies.

## Quick start on Windows

### Prerequisites

- Windows 10 or 11;
- Docker Desktop with Docker Compose;
- DeepSeek API access for remote paper understanding and copy generation;
- DashScope/Qwen Image access for hosted image generation;
- optionally, an NVIDIA GPU with ComfyUI, RealVisXL, and IP-Adapter.

JDK 21 is recommended only when developing the Java service directly. Docker performs the normal website build.

### Start the local stack

From the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1
```

The setup script creates `.env` without overwriting an existing one, generates local secrets, and prints the one-time bootstrap administrator password. The start script builds missing images, imports and activates the bundled n8n workflow, checks service health, and attempts to start an optional local ComfyUI installation when configured.

Open:

- application: `http://127.0.0.1:8080/`
- n8n: `http://127.0.0.1:5679/`
- renderer health: `http://127.0.0.1:3001/health`
- local assets: `http://127.0.0.1:8088/`

Then register or sign in, open Settings, configure your own text and image provider keys, upload a PDF, select the audience and visual options, generate candidates, and review the result.

### Stop the stack

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop.ps1
```

Docker volumes retain application data, n8n state, uploads, and renderer caches between starts.

## Direct development

The Java application is in `website/` and targets Java 21:

```powershell
cd website
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

The renderer is a private Node.js package:

```powershell
cd image-workflow\renderer-api
npm ci
npm start
```

The complete workflow depends on multiple services, so Docker Compose is the recommended integration environment.

## Verification

The repository includes several layers of validation:

- Spring Boot, MockMvc, provider, cache, evidence, and image-pipeline tests;
- Node.js syntax validation for the browser application;
- health endpoints for the website, n8n, and renderer;
- an importable and publishable n8n workflow;
- Docker image builds for the website and renderer;
- manual candidate review in the browser.

`docs/VALIDATION.md` records the validation performed for this shareable snapshot, including a clean-volume Docker startup, successful service health checks, a complete Maven test run, and browser-script syntax validation. Re-run the checks in your own environment before deployment because remote providers, model access, and GPU availability are environment-specific.

## Known limitations

- Final content quality depends on the uploaded paper and the configured provider models.
- Remote provider calls can fail, time out, cost money, or change behavior independently of this repository.
- GROBID, DOI enrichment, Qwen, and ComfyUI are optional integrations; fallbacks preserve workflow continuity but not identical output quality.
- OCR and local vision scores are screening signals, not scientific correctness guarantees.
- The system does not replace human verification of claims, image rights, or publication suitability.
- The bundled front end is a large static application rather than a fully componentized production frontend.
- A repository-wide open-source license is not currently declared; do not assume permission for third-party reuse.

## Further documentation

- [`docs/INSTALL-AND-USE.md`](docs/INSTALL-AND-USE.md): installation and user workflow
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): architecture and image-processing flow
- [`docs/SECURITY.md`](docs/SECURITY.md): security and sharing checklist
- [`docs/VALIDATION.md`](docs/VALIDATION.md): snapshot validation record
- [`docs/VISUAL-PROFILES-USAGE.md`](docs/VISUAL-PROFILES-USAGE.md): visual modes and profiles
- [`DEPLOY-ONLINE.md`](DEPLOY-ONLINE.md): online deployment notes
- [`DEPLOY-CLOUDFLARE.md`](DEPLOY-CLOUDFLARE.md): Cloudflare-oriented deployment notes

## Project scope

PaperLens is an engineering prototype for evidence-aware research communication. It demonstrates system integration, workflow resilience, grounded generation, image-pipeline design, security-aware secret handling, test-driven backend development, and human-in-the-loop review. It should not be represented as an autonomous scientific fact checker or a production-ready publication service without further operational hardening.
