import Foundation
import SwiftUI

struct CalcResult: Identifiable, Codable, Equatable {
    var id: UUID = UUID()
    var input: String
    var symbolic: String = ""
    var numeric: String = ""
    var latex: String = ""
    var numericLatex: String = ""
    var error: String? = nil
    var mode: EvalMode = .auto
    var backend: String = "native"
    var isPlot: Bool = false
    var plotData: String = ""

    var isError: Bool { error != nil }
    var primary: String { error ?? (symbolic.isEmpty ? numeric : symbolic) }
    var secondary: String? {
        guard !numeric.isEmpty, numeric != symbolic else { return nil }
        return numeric
    }
}

enum EvalMode: String, CaseIterable, Codable, Identifiable {
    case auto = "Auto"
    case exact = "Exact"
    case approx = "Approx"
    case rawXcas = "Raw Xcas"
    var id: String { rawValue }
}

enum ThemeMode: String, CaseIterable, Codable, Identifiable {
    case system = "System"
    case light = "Light"
    case dark = "Dark"
    var id: String { rawValue }
    var colorScheme: ColorScheme? {
        switch self { case .system: nil; case .light: .light; case .dark: .dark }
    }
}

enum AppLanguage: String, CaseIterable, Codable, Identifiable {
    case system = "System"
    case english = "English"
    case chinese = "中文"
    var id: String { rawValue }
    var giacCode: Int32 {
        switch self {
        case .chinese: 8
        case .english: 2
        case .system: Locale.current.language.languageCode?.identifier == "zh" ? 8 : 2
        }
    }
}

enum AngleUnit: String, CaseIterable, Codable, Identifiable {
    case rad = "Rad"
    case deg = "Deg"
    var id: String { rawValue }
}

struct AppSettings: Codable, Equatable {
    var themeMode: ThemeMode = .system
    var angleUnit: AngleUnit = .rad
    var precision: Int = 12
    var defaultEvalMode: EvalMode = .auto
    var language: AppLanguage = .system
    var autocompleteEnabled: Bool = true
    var syntaxHighlighting: Bool = true
    var historyLimit: Int = 64
}

struct HistoryEntry: Identifiable, Codable, Equatable {
    var id: UUID = UUID()
    var expression: String
    var result: String
    var numeric: String
    var latex: String
    var numericLatex: String
    var mode: EvalMode
    var timestamp: Date = .now
    var isPlot: Bool = false
    var plotData: String = ""
}

struct HelpEntry: Identifiable, Hashable {
    var id: String { name }
    var name: String
    var description: String
    var related: [String] = []
    var examples: [String] = []
    var signature: String = ""
    var aliases: [String] = []
    var syntax: String { signature.isEmpty ? "\(name)(…)" : "\(name)(\(signature))" }
}

struct PlotPoint: Codable, Equatable, Hashable {
    var x: Double
    var y: Double
}

enum PlotItem: Equatable {
    case curve(variable: String, xmin: Double, xmax: Double, points: [PlotPoint])
    case scatter(points: [PlotPoint])
    case surface3d(variable1: String, variable2: String, xmin: Double, xmax: Double, ymin: Double, ymax: Double, z: [[Double?]])
}

struct ReleaseInfo: Codable, Equatable {
    var version: String
    var pageURL: URL
}

enum AppTab: String, CaseIterable, Identifiable, Hashable {
    case calculator
    case help
    case history
    case settings
    var id: String { rawValue }
    var title: String {
        switch self { case .calculator: "Calculator"; case .help: "Help"; case .history: "History"; case .settings: "Settings" }
    }
    var symbol: String {
        switch self { case .calculator: "function"; case .help: "book"; case .history: "clock.arrow.circlepath"; case .settings: "gearshape" }
    }
}
