import SwiftUI

struct PlotView: View {
    let items: [PlotItem]
    @Environment(\.dismiss) private var dismiss
    @State private var showGrid = true
    @State private var scale: CGFloat = 1
    @State private var baseScale: CGFloat = 1
    @State private var pan = CGSize.zero
    @State private var basePan = CGSize.zero
    @State private var rotation: CGSize = CGSize(width: 0.62, height: -0.72)
    @State private var baseRotation = CGSize(width: 0.62, height: -0.72)

    private var hasSurface: Bool { items.contains { if case .surface3d = $0 { return true }; return false } }

    var body: some View {
        NavigationStack {
            Group {
                if items.isEmpty {
                    VStack(spacing: 10) {
                        Image(systemName: "chart.xyaxis.line").font(.largeTitle).foregroundColor(.secondary)
                        Text("No plot data").font(.headline)
                        Text("Evaluate a plot expression first.").font(.subheadline).foregroundColor(.secondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    PlotCanvas(items: items, showGrid: showGrid, scale: scale, pan: pan, rotation: rotation, compact: false)
                        .padding()
                        .contentShape(Rectangle())
                        .gesture(dragGesture)
                        .simultaneousGesture(magnifyGesture)
                }
            }
            .navigationTitle(hasSurface ? "3D Plot" : "Plot")
            .toolbar {
                ToolbarItemGroup(placement: .topBarLeading) {
                    Button { showGrid.toggle() } label: { Image(systemName: showGrid ? "grid" : "grid.slash") }.accessibilityLabel("Toggle grid")
                    Button { reset() } label: { Image(systemName: "arrow.counterclockwise") }.accessibilityLabel("Reset view")
                }
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
        }
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 2)
            .onChanged { value in
                if hasSurface {
                    rotation = CGSize(width: baseRotation.width + value.translation.height * 0.008, height: baseRotation.height + value.translation.width * 0.008)
                } else {
                    pan = CGSize(width: basePan.width + value.translation.width, height: basePan.height + value.translation.height)
                }
            }
            .onEnded { _ in
                baseRotation = rotation
                basePan = pan
            }
    }

    private var magnifyGesture: some Gesture {
        MagnificationGesture()
            .onChanged { value in scale = min(8, max(0.25, baseScale * value)) }
            .onEnded { _ in baseScale = scale }
    }

    private func reset() {
        scale = 1; baseScale = 1; pan = .zero; basePan = .zero
        rotation = CGSize(width: 0.62, height: -0.72); baseRotation = rotation
    }
}

struct PlotCanvas: View {
    let items: [PlotItem]
    let showGrid: Bool
    let scale: CGFloat
    let pan: CGSize
    let rotation: CGSize
    var compact: Bool = false

    var body: some View {
        Canvas { context, size in
            let rect = CGRect(x: 44, y: 18, width: max(1, size.width - 64), height: max(1, size.height - 52))
            if items.contains(where: { if case .surface3d = $0 { return true }; return false }) {
                drawSurface(items, context: &context, rect: rect)
            } else {
                draw2D(items, context: &context, rect: rect)
            }
        }
        .frame(minHeight: compact ? 150 : 320)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(.secondary.opacity(0.2)))
    }

    private func draw2D(_ items: [PlotItem], context: inout GraphicsContext, rect: CGRect) {
        let bounds = bounds2D(items)
        let xSpan = max(bounds.xmax - bounds.xmin, 1e-9)
        let ySpan = max(bounds.ymax - bounds.ymin, 1e-9)
        let transformed = rect.offsetBy(dx: pan.width, dy: pan.height)
        if showGrid {
            for i in 0...10 {
                let x = transformed.minX + transformed.width * CGFloat(i) / 10
                let y = transformed.minY + transformed.height * CGFloat(i) / 10
                var vertical = Path(); vertical.move(to: CGPoint(x: x, y: transformed.minY)); vertical.addLine(to: CGPoint(x: x, y: transformed.maxY))
                var horizontal = Path(); horizontal.move(to: CGPoint(x: transformed.minX, y: y)); horizontal.addLine(to: CGPoint(x: transformed.maxX, y: y))
                context.stroke(vertical, with: .color(.secondary.opacity(0.16)), style: StrokeStyle(lineWidth: 0.6))
                context.stroke(horizontal, with: .color(.secondary.opacity(0.16)), style: StrokeStyle(lineWidth: 0.6))
            }
        }
        var axisX = Path(); axisX.move(to: CGPoint(x: transformed.minX, y: transformed.maxY - CGFloat((0 - bounds.ymin) / ySpan) * transformed.height)); axisX.addLine(to: CGPoint(x: transformed.maxX, y: transformed.maxY - CGFloat((0 - bounds.ymin) / ySpan) * transformed.height))
        var axisY = Path(); axisY.move(to: CGPoint(x: transformed.minX + CGFloat((0 - bounds.xmin) / xSpan) * transformed.width, y: transformed.minY)); axisY.addLine(to: CGPoint(x: transformed.minX + CGFloat((0 - bounds.xmin) / xSpan) * transformed.width, y: transformed.maxY))
        context.stroke(axisX, with: .color(.secondary.opacity(0.7)), style: StrokeStyle(lineWidth: 1))
        context.stroke(axisY, with: .color(.secondary.opacity(0.7)), style: StrokeStyle(lineWidth: 1))

        for (index, item) in items.enumerated() {
            switch item {
            case .curve(_, _, _, let points): drawCurve(points, color: color(index), context: &context, rect: transformed, xMin: bounds.xmin, xSpan: xSpan, yMin: bounds.ymin, ySpan: ySpan)
            case .scatter(let points):
                for point in points {
                    let p = map(point, rect: transformed, xMin: bounds.xmin, xSpan: xSpan, yMin: bounds.ymin, ySpan: ySpan)
                    context.fill(Path(ellipseIn: CGRect(x: p.x - 3, y: p.y - 3, width: 6, height: 6)), with: .color(color(index)))
                }
            case .surface3d: break
            }
        }
        context.draw(Text(String(format: "x: %.4g … %.4g", bounds.xmin, bounds.xmax)).font(.caption).foregroundColor(.secondary), at: CGPoint(x: rect.midX, y: rect.maxY + 28))
        context.draw(Text(String(format: "y: %.4g … %.4g", bounds.ymin, bounds.ymax)).font(.caption).foregroundColor(.secondary), at: CGPoint(x: 22, y: rect.midY), anchor: .center)
    }

    private func drawSurface(_ items: [PlotItem], context: inout GraphicsContext, rect: CGRect) {
        guard let surface = items.first(where: { if case .surface3d = $0 { return true }; return false }),
              case let .surface3d(_, _, xmin, xmax, ymin, ymax, z) = surface else { return }
        guard let cols = z.first?.count, cols > 1, z.count > 1 else { return }
        let valid = z.flatMap { $0.compactMap { $0 }.filter { $0.isFinite } }
        guard let zMin = valid.min(), let zMax = valid.max() else { return }
        let zSpan = max(zMax - zMin, 1e-9)
        func project(_ row: Int, _ col: Int) -> CGPoint? {
            guard row < z.count, col < z[row].count, let value = z[row][col], value.isFinite else { return nil }
            let x = CGFloat(Double(col) / Double(cols - 1) * 2 - 1)
            let y = CGFloat(Double(row) / Double(z.count - 1) * 2 - 1)
            let zz = CGFloat((value - zMin) / zSpan * 2 - 1)
            let cy = cos(rotation.height), sy = sin(rotation.height), cx = cos(rotation.width), sx = sin(rotation.width)
            let x1 = x * cy - zz * sy
            let z1 = x * sy + zz * cy
            let y1 = y * cx - z1 * sx
            let center = CGPoint(x: rect.midX + pan.width, y: rect.midY + pan.height)
            let factor = min(rect.width, rect.height) * 0.34 * scale
            return CGPoint(x: center.x + x1 * factor, y: center.y - y1 * factor)
        }
        if showGrid {
            for row in 0..<z.count {
                for col in 0..<cols {
                    if let p = project(row, col) {
                        if col + 1 < cols, let q = project(row, col + 1) { stroke(p, q, color: .secondary.opacity(0.24), context: &context) }
                        if row + 1 < z.count, let q = project(row + 1, col) { stroke(p, q, color: .secondary.opacity(0.24), context: &context) }
                    }
                }
            }
        }
        for row in 0..<(z.count - 1) {
            for col in 0..<(cols - 1) {
                guard let a = project(row, col), let b = project(row, col + 1), let c = project(row + 1, col + 1), let d = project(row + 1, col) else { continue }
                let value = z[row][col] ?? zMin
                let t = max(0, min(1, (value - zMin) / zSpan))
                var path = Path(); path.move(to: a); path.addLine(to: b); path.addLine(to: c); path.addLine(to: d); path.closeSubpath()
                context.fill(path, with: .color(Color(hue: 0.68 - 0.68 * t, saturation: 0.72, brightness: 0.92).opacity(0.36)))
            }
        }
        context.draw(Text(String(format: "x: %.4g … %.4g   y: %.4g … %.4g", xmin, xmax, ymin, ymax)).font(.caption).foregroundColor(.secondary), at: CGPoint(x: rect.midX, y: rect.maxY + 28))
    }

    private func stroke(_ a: CGPoint, _ b: CGPoint, color: Color, context: inout GraphicsContext) {
        var path = Path(); path.move(to: a); path.addLine(to: b); context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 0.8))
    }

    private func drawCurve(_ points: [PlotPoint], color: Color, context: inout GraphicsContext, rect: CGRect, xMin: Double, xSpan: Double, yMin: Double, ySpan: Double) {
        guard points.count > 1 else { return }
        var path = Path()
        var previous: PlotPoint?
        for point in points {
            let shouldBreak = previous.map { abs(point.x - $0.x) > xSpan / Double(max(points.count, 2)) * 8 || abs(point.y - $0.y) > ySpan * 0.8 } ?? true
            let mapped = map(point, rect: rect, xMin: xMin, xSpan: xSpan, yMin: yMin, ySpan: ySpan)
            if shouldBreak { if !path.isEmpty { context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 2)) }; path = Path(); path.move(to: mapped) } else { path.addLine(to: mapped) }
            previous = point
        }
        if !path.isEmpty { context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 2)) }
    }

    private func map(_ point: PlotPoint, rect: CGRect, xMin: Double, xSpan: Double, yMin: Double, ySpan: Double) -> CGPoint {
        CGPoint(x: rect.minX + CGFloat((point.x - xMin) / xSpan) * rect.width, y: rect.maxY - CGFloat((point.y - yMin) / ySpan) * rect.height)
    }

    private func bounds2D(_ items: [PlotItem]) -> (xmin: Double, xmax: Double, ymin: Double, ymax: Double) {
        let points = items.flatMap { item -> [PlotPoint] in
            switch item { case .curve(_, _, _, let points), .scatter(let points): return points; case .surface3d: return [] }
        }
        let xMin = items.compactMap { if case .curve(_, let a, _, _) = $0 { return a }; return nil }.min() ?? points.map(\.x).min() ?? -10
        let xMax = items.compactMap { if case .curve(_, _, let b, _) = $0 { return b }; return nil }.max() ?? points.map(\.x).max() ?? 10
        let yValues = points.map(\.y)
        let yMin = yValues.min() ?? -1, yMax = yValues.max() ?? 1
        let pad = max((yMax - yMin) * 0.08, 0.5)
        return (xMin, max(xMax, xMin + 1e-6), yMin - pad, yMax + pad)
    }

    private func color(_ index: Int) -> Color { [.blue, .orange, .green, .purple, .pink][index % 5] }
}


