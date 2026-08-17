import Foundation

struct ExpressionFormatter {
    static func toEngineInput(_ source: String) -> String {
        source
            .replacingOccurrences(of: "×", with: "*")
            .replacingOccurrences(of: "·", with: "*")
            .replacingOccurrences(of: "÷", with: "/")
            .replacingOccurrences(of: "−", with: "-")
            .replacingOccurrences(of: "π", with: "pi")
            .replacingOccurrences(of: "∞", with: "infinity")
            .replacingOccurrences(of: "≤", with: "<=")
            .replacingOccurrences(of: "≥", with: ">=")
            .replacingOccurrences(of: "≠", with: "!=")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

final class CalcoraEngine: @unchecked Sendable {
    static let shared = CalcoraEngine()

    private let lock = NSLock()
    private var initialized = false

    private init() {}

    @discardableResult
    func initialize() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if initialized { return true }
        calcora_engine_init()
        initialized = true
        return true
    }

    func setHelpDirectory(_ path: String) {
        guard initialize() else { return }
        lock.withLock { path.withCString { calcora_engine_set_help_dir($0) } }
    }

    func evaluate(_ input: String, mode: EvalMode) -> CalcResult {
        guard initialize() else {
            return CalcResult(input: input, error: "Native backend failed to load", mode: mode, backend: "unavailable")
        }
        let normalized = ExpressionFormatter.toEngineInput(input)
        let raw: String = lock.withLock {
            normalized.withCString { expression in
                mode.rawValue.withCString { modeName in
                    copyCString(calcora_engine_evaluate(expression, modeName))
                }
            }
        }
        return parseResult(input: input, mode: mode, raw: raw)
    }

    func evaluateRawXcas(_ input: String) -> CalcResult { evaluate(input, mode: .rawXcas) }

    func resetSession() {
        guard initialize() else { return }
        lock.withLock { calcora_engine_reset() }
    }

    func interrupt() {
        guard initialize() else { return }
        calcora_engine_interrupt()
    }

    func setLanguage(_ language: AppLanguage) {
        guard initialize() else { return }
        lock.withLock { calcora_engine_set_language(language.giacCode) }
    }

    func setPrecision(_ digits: Int) {
        guard initialize() else { return }
        lock.withLock { calcora_engine_set_precision(Int32(max(4, min(32, digits)))) }
    }

    func setAngleUnit(_ unit: AngleUnit) {
        guard initialize() else { return }
        lock.withLock { calcora_engine_set_angle_unit(unit == .deg ? 1 : 0) }
    }

    func version() -> String {
        guard initialize() else { return "native unavailable" }
        return lock.withLock { copyCString(calcora_engine_version()) }
    }

    func help(_ command: String) -> String {
        guard initialize() else { return "" }
        return lock.withLock {
            command.withCString { copyCString(calcora_engine_help($0)) }
        }
    }

    func plotSample(expression: String, variable: String = "x", xmin: Double = -10, xmax: Double = 10, samples: Int = 500) -> [PlotPoint] {
        guard initialize() else { return [] }
        let raw: String = lock.withLock {
            expression.withCString { expr in
                variable.withCString { variableName in
                    copyCString(calcora_engine_plot_sample(expr, variableName, xmin, xmax, Int32(samples)))
                }
            }
        }
        guard let data = raw.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [[Double]] else { return [] }
        return json.compactMap { pair in
            guard pair.count >= 2, pair[0].isFinite, pair[1].isFinite else { return nil }
            return PlotPoint(x: pair[0], y: pair[1])
        }
    }

    func plotItems(from result: CalcResult) -> [PlotItem] {
        guard let data = result.plotData.data(using: .utf8),
              let rawItems = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            let points = plotSample(expression: result.input)
            return points.isEmpty ? [] : [.curve(variable: "x", xmin: -10, xmax: 10, points: points)]
        }
        return rawItems.compactMap { item in
            let type = item["type"] as? String
            let points = parsePoints(item["pts"] as? [[Any]])
            if type == "scatter" { return .scatter(points: points) }
            if type == "surface3d" {
                let z = (item["z"] as? [[Any]])?.map { row in row.map { value in value as? Double } } ?? []
                return .surface3d(
                    variable1: item["var1"] as? String ?? "x",
                    variable2: item["var2"] as? String ?? "y",
                    xmin: item["xmin"] as? Double ?? -5,
                    xmax: item["xmax"] as? Double ?? 5,
                    ymin: item["ymin"] as? Double ?? -5,
                    ymax: item["ymax"] as? Double ?? 5,
                    z: z
                )
            }
            return .curve(
                variable: item["var"] as? String ?? "x",
                xmin: item["xmin"] as? Double ?? -10,
                xmax: item["xmax"] as? Double ?? 10,
                points: points
            )
        }
    }

    private func parsePoints(_ raw: [[Any]]?) -> [PlotPoint] {
        (raw ?? []).compactMap { pair in
            guard pair.count >= 2,
                  let x = pair[0] as? Double,
                  let y = pair[1] as? Double,
                  x.isFinite, y.isFinite else { return nil }
            return PlotPoint(x: x, y: y)
        }
    }

    private func parseResult(input: String, mode: EvalMode, raw: String) -> CalcResult {
        guard let data = raw.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return CalcResult(input: input, symbolic: raw, mode: mode)
        }
        return CalcResult(
            input: input,
            symbolic: object["symbolic"] as? String ?? "",
            numeric: object["numeric"] as? String ?? "",
            latex: object["latex"] as? String ?? "",
            numericLatex: object["numericLatex"] as? String ?? "",
            error: (object["error"] as? String).flatMap { $0.isEmpty ? nil : $0 },
            mode: mode,
            backend: object["backend"] as? String ?? "native",
            isPlot: object["isGraphic"] as? Bool ?? false,
            plotData: object["plotData"] as? String ?? ""
        )
    }
}

private func copyCString(_ pointer: UnsafePointer<CChar>?) -> String {
    guard let pointer else { return "" }
    return String(cString: pointer)
}

private extension NSLock {
    func withLock<T>(_ body: () -> T) -> T {
        lock()
        defer { unlock() }
        return body()
    }
}

