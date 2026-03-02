# Lc_Analyzer — Fiji Plugin

**Author:** Saiful Islam  
**Version:** 1.0.0  
**License:** MIT

A Fiji/ImageJ plugin for Chemi vs Membrane ROI threshold and particle analysis.

---

## Features

- **F1** — Duplicates the current rectangular ROI selection, inverts it (Chemi mode), and opens the Colour Threshold dialog.
- **F2** — Closes the Colour Threshold dialog, then:
  - **Chemi mode:** runs Measure
  - **Membrane mode:** runs Analyse Particles (size ≥ 80 px, outlines + overlay)
- Mode (chemi / membrane) is auto-detected from the image filename.

---

## Requirements

- [Fiji](https://fiji.sc/) (includes ImageJ)
- Java 8 or higher
- Maven 3.x (only needed to build from source)

---

## Build from Source

```bash
# 1. Clone or download this project
cd Lc_Analyzer

# 2. Build with Maven
mvn clean package

# 3. The plugin jar will be at:
#    target/Lc-Analyzer.jar
```

---

## Installation in Fiji

1. Build the jar (see above), or download a pre-built `Lc-Analyzer.jar`.
2. Copy `Lc-Analyzer.jar` into your Fiji `plugins/` folder:
   - **macOS:** `Fiji.app/plugins/`
   - **Windows:** `Fiji/plugins/`
   - **Linux:** `Fiji.app/plugins/`
3. Restart Fiji.
4. The plugin will appear under **Plugins > Lc_Analyzer**.

---

## Usage

1. Open Fiji and run **Plugins > Lc_Analyzer > Lc_Analyzer**.
2. Select your image file in the dialog.
3. Draw a **rectangle ROI** on the image.
4. Press **F1** — the ROI is duplicated and the Colour Threshold dialog opens.
5. Adjust the threshold as needed.
6. Press **F2** — analysis runs automatically based on the detected mode.
7. Repeat from step 3 for the next ROI.

---

## Project Structure

```
Lc_Analyzer/
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── java/
        │   └── sc/fiji/lc_analyzer/
        │       └── Lc_Analyzer.java
        └── resources/
            └── plugins.config
```

---

## License

MIT License — free to use, modify, and distribute.
