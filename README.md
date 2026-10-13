# HDE Studio - Architectural CAD & Floor Plan Studio

A professional, standalone 2D Architectural CAD application written in Java Swing, designed for precision floor planning, technical working drawings, and social media presentation export (Pinterest, LinkedIn, YouTube, Instagram).

![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)
![Architecture](https://img.shields.io/badge/AutoCAD-2D%20Drafting-blue.svg)
![License](https://img.shields.io/badge/License-MIT-green.svg)

---

## Features

### 1. Wall Drafting & Automatic Corner Welds
- Multi-thickness wall system: 9" Exterior Brickwork, 6" Interior Partitions, 4.5" Thin Wall Partitions.
- Auto-join & endpoint welding algorithm for clean corners and T-junctions.
- Real-time wall collision detection and clearance checks.
- Three architectural wall hatch styles: ANSI31 45° diagonal masonry hatch, Solid Black Poché, and Hollow.

### 2. AutoCAD-Style Polygonal Hatch & Flooring Finishes
- Interactive AutoCAD-style perimeter hatch tool: Click corners around any boundary to generate clipped vector hatches.
- Architectural patterns: 2'×2' Vitrified Tiles, 1'×1' Ceramic, 2'×4' Porcelain/Marble slabs, 45° Diagonal Tiles, Hardwood Planks with staggered joints, Mosaic tiles, and Outdoor Pavers.

### 3. Comprehensive CAD Block Catalog
- **Openings:** Single swing doors (90° open leaf + quarter-circle swing arc + threshold), Double French doors, Sliding bypass doors, Pocket doors with wall cavities, Bifold chevrons, Casement windows with sill projections, Sliding windows, and Ventilators.
- **Furniture:** King/Single beds with 2D technical pillow outlines and duvet folds, Sofas (3-seat, armchairs), Dining sets with chairs, Desks with swivel chairs, Wardrobes with standard AutoCAD closet cross-lines (`X`), TV units, and Bookshelves.
- **Sanitaryware:** Toilets (cistern, dual flush, oval bowl and seat rim), Wash basins, Bathtubs with drainage & faucets, Walk-in shower stalls.
- **Kitchens:** L-Shape, U-Shape, Parallel Galley, Island with Breakfast Bar & Stools, Peninsula, Linear, Compact, Built-in Ovens, Dishwashers, Stoves, and Fridges.
- **Structural & Electrical:** Columns, Dog-leg & Straight stairs with "UP" arrows, Ceiling fans, AC split units, Light points, and Switchboards.
- **Landscape & Vehicles:** Top-down CAD Sedan, SUV, Motorcycles, Scooters, Bicycles, Deciduous canopy trees with scalloped outlines, Pine trees, Planters, Road segments, and Pathways.
- **Pre-Arranged Room Suites:** Master bedroom suite, Double bedroom suite, Compact guest room, Twin kids room, and Master bath suite.

### 4. Precision Drafting Controls
- **Smooth Cursor Movement by Default:** Unrestricted, smooth drawing with zero grid locking.
- **Background Grid Display (`F7`):** Toggle drafting grid lines on and off with a single click or keyboard shortcut.
- **Precision Snap (`F3`):** Instant 1-foot grid snap toggle when strict alignment is required.
- **Orthogonal Lock (`F8`):** Restricts wall drafting to strict 90° horizontal and vertical alignments.

### 5. Architectural Export Engine (PDF & PNG)
- **Standard Sheet Presets & Social Media Formats:**
  - Pinterest / Infographics (9:16 vertical 1200×1800, 1080×1920)
  - LinkedIn / Instagram Portrait (4:5 vertical 1080×1350)
  - Square Showcase (1:1 1200×1200)
  - YouTube / Video Landscape (16:9 widescreen 1920×1080, 4K 3840×2160)
  - ISO Standard Print Sheets: A4 Landscape (2480×1754), A4 Portrait (1754×2480), A3 Sheet (3508×2480)
  - Custom user-specified dimensions (200px to 8000px).
- **Layout Styles:**
  - **Social Media Clean:** Maximizes floor plan bounding box to occupy ~92% of the canvas with slim margins and a discrete technical watermark.
  - **Contractor Sheet:** Standard double border framing with full contractor title block stamp (project title, status, scale, sheet number, date).
- **Color & Style Modes:**
  - **Monochrome:** Pure black (`#000000`) vector linework on crisp white paper for traditional blueprints.
  - **Color:** Standard AutoCAD architectural layer pen colors (Furniture Navy `#0369a1`, Casework Teal `#0f766e`, Plumbing Cyan `#0ea5e9`, Equipment Slate `#334155`, Landscape Green `#15803d`) with clean white wipeouts.
- **Live Preview:** Interactive real-time preview canvas in the export modal displaying exact proportions, orientation, and framing before export.

---

## Getting Started

### Prerequisites
- Java Development Kit (JDK) 11 or higher.

### Compile and Run
```bash
# Windows
javac -encoding UTF-8 -d . HousePlanCADApp.java
java -cp . cadstudio.HousePlanCADApp
```
Or simply run:
```bat
run.bat
```

---

## Keyboard Shortcuts
| Shortcut | Action |
| :--- | :--- |
| `F7` | Toggle Background Drafting Grid (Show/Hide) |
| `F8` | Toggle Orthogonal Constraint (ORTHO) |
| `F3` | Toggle 1-Foot Precision Snap |
| `Ctrl + Z` | Undo |
| `Ctrl + Y` | Redo |
| `Space` | Auto-Join / Weld Walls |
| `Delete` / `Backspace` | Delete Selected Element |
| `Escape` | Clear Selection / Cancel Polygon Hatch |
| `P` | Activate AutoCAD Polygon Hatch Tool |
| `W` | Activate Wall Drawing Tool |
| `H` | Activate Pan Tool |
| `+` / `-` | Zoom In / Out |
