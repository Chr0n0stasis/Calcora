import Foundation
import Combine
import SwiftUI

@MainActor
final class CalcoraStore: ObservableObject {
    @Published var settings: AppSettings { didSet { persistSettings(); applyEngineSettings() } }
    @Published var selectedTab: AppTab = .calculator
    @Published var expression = ""
    @Published var selectedMode: EvalMode
    @Published var result: CalcResult?
    @Published var history: [HistoryEntry]
    @Published var isEvaluating = false
    @Published var terminalInput = ""
    @Published var terminalLines: [String] = []
    @Published var scriptText = "sin(x)\ncos(x)\n"
    @Published var scriptOutput: [String] = []
    @Published var scriptName = "Untitled.xcas"
    @Published var helpQuery = ""
    @Published var helpEntries: [HelpEntry] = []
    @Published var selectedHelp: HelpEntry?
    @Published var updateMessage = ""

    private let settingsKey = "calcora.native.settings"
    private let historyKey = "calcora.native.history"
    private var evaluationGeneration = 0
    private var terminalGeneration = 0
    private var scriptGeneration = 0

    init() {
        let loadedSettings = Self.decode(AppSettings.self, key: settingsKey) ?? AppSettings()
        let loadedHistory = Self.decode([HistoryEntry].self, key: historyKey) ?? []
        settings = loadedSettings
        selectedMode = loadedSettings.defaultEvalMode
        history = Array(loadedHistory.prefix(max(1, loadedSettings.historyLimit)))
        CalcoraEngine.shared.initialize()
        applyEngineSettings()
        configureHelpDirectory()
        helpEntries = loadHelpEntries(language: settings.language)
    }

    var scriptNames: [String] {
        guard let names = try? FileManager.default.contentsOfDirectory(at: scriptsDirectory, includingPropertiesForKeys: nil)
            .filter({ $0.pathExtension.lowercased() == "xcas" })
            .map({ $0.deletingPathExtension().lastPathComponent })
            .sorted() else { return [] }
        return names
    }

    var autocompleteSuggestions: [String] {
        guard settings.autocompleteEnabled else { return [] }
        let trimmed = expression.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let query = trimmed.split(whereSeparator: { $0 == " " || $0 == "(" || $0 == "," || $0 == ";" }).last.map(String.init) ?? ""
        guard !query.isEmpty else { return [] }
        let source = helpEntries.map(\.name)
        return source.filter { $0.localizedCaseInsensitiveContains(query) }.prefix(8).map { $0 }
    }

    func evaluate() {
        let input = expression.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !input.isEmpty else { result = CalcResult(input: "", error: "Enter an expression.", mode: selectedMode); return }
        evaluationGeneration += 1
        let generation = evaluationGeneration
        let mode = selectedMode
        isEvaluating = true
        let engine = CalcoraEngine.shared
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let evaluated = engine.evaluate(input, mode: mode)
            DispatchQueue.main.async {
                guard let self, self.evaluationGeneration == generation else { return }
                self.result = evaluated
                self.isEvaluating = false
                self.appendHistory(evaluated)
            }
        }
    }

    func evaluateAndAppend(_ text: String) {
        expression = text
        selectedTab = .calculator
        evaluate()
    }

    func submitTerminal() {
        let command = terminalInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !command.isEmpty else { return }
        terminalGeneration += 1
        let generation = terminalGeneration
        terminalLines.append("> \(command)")
        terminalInput = ""
        let engine = CalcoraEngine.shared
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let response = engine.evaluateRawXcas(command)
            DispatchQueue.main.async {
                guard let self, self.terminalGeneration == generation else { return }
                self.terminalLines.append(response.isError ? (response.error ?? "Error") : response.primary)
                if let secondary = response.secondary { self.terminalLines.append(secondary) }
            }
        }
    }

    func runScript() {
        let lines = scriptText.components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !$0.hasPrefix("#") }
        guard !lines.isEmpty else { scriptOutput = ["No executable statements."]; return }
        scriptGeneration += 1
        let generation = scriptGeneration
        scriptOutput = ["Running \(lines.count) statement\(lines.count == 1 ? "" : "s")…"]
        let engine = CalcoraEngine.shared
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            var outputs: [String] = []
            for line in lines {
                let response = engine.evaluateRawXcas(line)
                outputs.append("> \(line)")
                outputs.append(response.isError ? (response.error ?? "Error") : response.primary)
            }
            DispatchQueue.main.async {
                guard let self, self.scriptGeneration == generation else { return }
                self.scriptOutput = outputs
            }
        }
    }

    func clearTerminal() { terminalLines.removeAll() }
    func clearScriptOutput() { scriptOutput.removeAll() }

    func clearHistory() { history.removeAll(); persistHistory() }
    func deleteHistory(at offsets: IndexSet) { history.remove(atOffsets: offsets); persistHistory() }

    func restore(_ entry: HistoryEntry) {
        expression = entry.expression
        selectedMode = entry.mode
        result = CalcResult(input: entry.expression, symbolic: entry.result, numeric: entry.numeric, latex: entry.latex, numericLatex: entry.numericLatex, mode: entry.mode, isPlot: entry.isPlot, plotData: entry.plotData)
        selectedTab = .calculator
    }

    func resetSession() {
        CalcoraEngine.shared.resetSession()
        result = nil
        terminalLines.append("Session reset.")
    }

    func setLanguage(_ language: AppLanguage) {
        settings.language = language
        configureHelpDirectory()
        helpEntries = loadHelpEntries(language: language)
    }

    func searchHelp() -> [HelpEntry] {
        let query = helpQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return Array(helpEntries.prefix(120)) }
        return helpEntries.filter {
            $0.name.localizedCaseInsensitiveContains(query) ||
            $0.aliases.contains(where: { $0.localizedCaseInsensitiveContains(query) }) ||
            $0.signature.localizedCaseInsensitiveContains(query) ||
            $0.description.localizedCaseInsensitiveContains(query) ||
            $0.related.contains(where: { $0.localizedCaseInsensitiveContains(query) }) ||
            $0.examples.contains(where: { $0.localizedCaseInsensitiveContains(query) })
        }
    }

    func helpDetail(for entry: HelpEntry) -> HelpEntry {
        let native = CalcoraEngine.shared.help(entry.name)
        guard !native.isEmpty else { return entry }
        var updated = entry
        if updated.description == "No description available." || updated.description.isEmpty {
            updated.description = native.replacingOccurrences(of: "\n", with: " ")
        }
        return updated
    }

    func plotItems() -> [PlotItem] { guard let result else { return [] }; return CalcoraEngine.shared.plotItems(from: result) }

    func checkForUpdate() {
        updateMessage = "Checking…"
        Task {
            do {
                var request = URLRequest(url: URL(string: "https://api.github.com/repos/Chr0n0stasis/Calcora/releases/latest")!)
                request.setValue("Calcora iOS", forHTTPHeaderField: "User-Agent")
                let (data, response) = try await URLSession.shared.data(for: request)
                guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { throw URLError(.badServerResponse) }
                struct Release: Decodable { let tag_name: String; let html_url: URL }
                let release = try JSONDecoder().decode(Release.self, from: data)
                let current = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
                updateMessage = release.tag_name == current || release.tag_name == "v\(current)" ? "You are up to date (\(current))." : "Latest: \(release.tag_name)"
            } catch {
                updateMessage = "Update check failed: \(error.localizedDescription)"
            }
        }
    }

    func saveScript(named name: String? = nil) throws {
        let raw = (name ?? scriptName).trimmingCharacters(in: .whitespacesAndNewlines)
        let safe = sanitizedScriptName(raw.isEmpty ? "Untitled" : raw)
        let url = scriptsDirectory.appendingPathComponent(safe).appendingPathExtension("xcas")
        try FileManager.default.createDirectory(at: scriptsDirectory, withIntermediateDirectories: true)
        try scriptText.write(to: url, atomically: true, encoding: .utf8)
        scriptName = url.deletingPathExtension().lastPathComponent + ".xcas"
        objectWillChange.send()
    }

    func loadScript(named name: String) throws {
        let safe = sanitizedScriptName(name)
        let url = scriptsDirectory.appendingPathComponent(safe).appendingPathExtension("xcas")
        scriptText = try String(contentsOf: url, encoding: .utf8)
        scriptName = url.lastPathComponent
        scriptOutput.removeAll()
    }

    func deleteScript(named name: String) throws {
        let url = scriptsDirectory.appendingPathComponent(sanitizedScriptName(name)).appendingPathExtension("xcas")
        try FileManager.default.removeItem(at: url)
    }

    func importScript(from url: URL) throws {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        scriptText = try String(contentsOf: url, encoding: .utf8)
        scriptName = url.lastPathComponent
        try saveScript(named: url.deletingPathExtension().lastPathComponent)
    }

    private var scriptsDirectory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("Scripts", isDirectory: true)
    }

    private func sanitizedScriptName(_ name: String) -> String {
        let base = name.replacingOccurrences(of: ".xcas", with: "", options: .caseInsensitive)
        let allowed = base.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) || "-_ .".unicodeScalars.contains($0) }
        return String(String.UnicodeScalarView(allowed)).trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: " ", with: "_")
    }

    private func appendHistory(_ value: CalcResult) {
        guard !value.input.isEmpty else { return }
        let entry = HistoryEntry(expression: value.input, result: value.primary, numeric: value.numeric, latex: value.latex, numericLatex: value.numericLatex, mode: value.mode, isPlot: value.isPlot, plotData: value.plotData)
        history.removeAll { $0.expression == entry.expression && $0.mode == entry.mode }
        history.insert(entry, at: 0)
        history = Array(history.prefix(max(1, settings.historyLimit)))
        persistHistory()
    }

    private func applyEngineSettings() {
        let engine = CalcoraEngine.shared
        engine.initialize()
        engine.setLanguage(settings.language)
        engine.setPrecision(settings.precision)
        engine.setAngleUnit(settings.angleUnit)
    }

    private func configureHelpDirectory() {
        let directory = settings.language == .chinese ? "zh" : nil
        if let path = Bundle.main.path(forResource: "aide_cas", ofType: nil, inDirectory: directory) {
            CalcoraEngine.shared.setHelpDirectory((path as NSString).deletingLastPathComponent)
        }
    }

    private func loadHelpEntries(language: AppLanguage) -> [HelpEntry] {
        let directory = language == .chinese ? "zh" : nil
        if let path = Bundle.main.path(forResource: "aide_cas", ofType: nil, inDirectory: directory), let text = try? String(contentsOfFile: path, encoding: .utf8) {
            let parsed = parseHelp(text: text, chinese: language == .chinese)
            if !parsed.isEmpty { return parsed }
        }
        return Self.fallbackHelp
    }

    private func parseHelp(text: String, chinese: Bool) -> [HelpEntry] {
        var entries: [HelpEntry] = []
        var current: HelpEntry?
        var examples: [String] = []
        func flush() {
            guard var current else { return }
            current.examples = examples
            entries.append(current)
            examples.removeAll()
        }
        for rawLine in text.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.hasPrefix("#") {
                flush()
                let names = line.dropFirst().trimmingCharacters(in: .whitespaces)
                    .split { $0 == "," || $0 == ";" || $0 == "|" }
                    .map { $0.trimmingCharacters(in: .whitespaces) }
                    .filter { !$0.isEmpty }
                let primary = names.first ?? ""
                current = HelpEntry(name: primary, description: "No description available.", aliases: Array(names.dropFirst()))
                continue
            }
            guard current != nil, !line.isEmpty else { continue }
            let parts = line.split(separator: " ", maxSplits: 1, omittingEmptySubsequences: true)
            guard let marker = parts.first, let code = Int(marker) else { examples.append(line); continue }
            let value = parts.count > 1 ? String(parts[1]) : ""
            switch code {
            case 0:
                current?.signature = value
            case -1, -2, -3:
                current?.related.append(contentsOf: value.split { $0 == "," || $0 == ";" }.map(String.init))
            case 2:
                if !chinese { current?.description = value }
            case 5:
                if chinese { current?.description = value }
            case 1:
                if !chinese, current?.description == "No description available." { current?.description = value }
            case 6...:
                examples.append(value)
            default:
                break
            }
        }
        flush()
        return entries.filter { !$0.name.isEmpty }.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    private func persistSettings() { if let data = try? JSONEncoder().encode(settings) { UserDefaults.standard.set(data, forKey: settingsKey) } }
    private func persistHistory() { if let data = try? JSONEncoder().encode(history) { UserDefaults.standard.set(data, forKey: historyKey) } }
    private static func decode<T: Decodable>(_ type: T.Type, key: String) -> T? { guard let data = UserDefaults.standard.data(forKey: key) else { return nil }; return try? JSONDecoder().decode(type, from: data) }

    private static let fallbackHelp: [HelpEntry] = [
        HelpEntry(name: "sin", description: "Sine function.", examples: ["sin(pi/2)"], signature: "x"),
        HelpEntry(name: "cos", description: "Cosine function.", examples: ["cos(0)"], signature: "x"),
        HelpEntry(name: "sqrt", description: "Square root.", examples: ["sqrt(2)"], signature: "x"),
        HelpEntry(name: "solve", description: "Solve an equation or a system.", examples: ["solve(x^2=4,x)"], signature: "equation, variable"),
        HelpEntry(name: "factor", description: "Factor an expression.", examples: ["factor(x^2-1)"], signature: "expression"),
        HelpEntry(name: "diff", description: "Differentiate an expression.", examples: ["diff(sin(x),x)"], signature: "expression, variable"),
        HelpEntry(name: "integrate", description: "Integrate an expression.", examples: ["integrate(x^2,x)"], signature: "expression, variable"),
        HelpEntry(name: "plot", description: "Plot a function.", examples: ["plot(sin(x),x)"], signature: "expression, variable")
    ]
}




