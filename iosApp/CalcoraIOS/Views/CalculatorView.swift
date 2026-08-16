import SwiftUI
import UIKit

struct CalculatorView: View {
    @EnvironmentObject private var store: CalcoraStore
    @State private var selectedRange: NSRange?
    @State private var showingTerminal = false
    @State private var showingScript = false
    @State private var showingPlot = false
    @State private var showingNaturalMath = false
    @State private var showingResultDetails = false
    @State private var showingHistory = false
    @State private var showingDebug = false
    @State private var activeTab: ExtraTab = .none

    enum ExtraTab {
        case none, vars, funcs, cas
    }

    private let functionButtons = [
        "sin(□)", "cos(□)", "tan(□)", "asin(□)", "acos(□)", "atan(□)",
        "sqrt(□)", "log(□)", "ln(□)", "exp(□)", "abs(□)", "floor(□)", "ceil(□)",
        "^", "integrate(□,x)", "diff(□,x)", "limit(□,x=0)", "sum(□,k,1,n)",
        "plot(□,x=-5..5)", "plot3d(□,x=-5..5,y=-5..5)"
    ]
    private let casButtons = [
        "solve(□=0,x)", "factor(□)", "expand(□)", "normal(□)",
        "subst(□,x=□)", "diff(□,x)", "diff(□,x,2)",
        "integrate(□,x)", "integrate(□,x,0,1)", "limit(□,x=0)",
        "sum(□,k,1,n)", "det(□)", "inv(□)", "transpose(□)", "rank(□)",
        "gcd(□,□)", "lcm(□,□)", "ifactor(□)", "simplify(□)",
        "plot(□,x=-5..5)", "plot3d(□,x=-5..5,y=-5..5)", "plotparam(□,t)",
        "makelist(□,k,1,n)", "makemat(□,n,p)", "fft(□)", "ifft(□)", "help(□)"
    ]
    private let variableButtons = [
        "x", "y", "z", "a", "b", "c", "n", "t", "k", "m", "ans", "π", "e",
        "(", ")", "[", "]", "{", "}", ";", ":=", "→"
    ]
    
    // Keypad layout
    private let keypadLayout: [[String]] = [
        ["AC", "()", "÷", "⌫"],
        ["7", "8", "9", "×"],
        ["4", "5", "6", "−"],
        ["1", "2", "3", "+"],
        ["0", ".", "^", "="]
    ]

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Expression and Result Area
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Spacer()
                        Picker("Mode", selection: $store.selectedMode) {
                            ForEach(EvalMode.allCases) { Text(LocalizedStringKey($0.rawValue)).tag($0) }
                        }
                        .pickerStyle(.menu)
                    }
                    .padding(.horizontal)
                    .padding(.top, 8)
                    .contentShape(Rectangle())
                    .onTapGesture { dismissKeyboard() }
                    
                    if showingDebug {
                        Text("debug: \(ExpressionFormatter.toEngineInput(store.expression))")
                            .font(.system(.caption, design: .monospaced))
                            .foregroundStyle(.secondary)
                            .textSelection(.enabled)
                            .padding(.horizontal)
                            .padding(.bottom, 8)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }

                    ExpressionTextView(text: $store.expression, selectedRange: $selectedRange)
                        .frame(maxHeight: .infinity)
                        .accessibilityLabel(LocalizedStringKey("Expression input"))
                    
                    if let result = store.result {
                        VStack(alignment: .trailing, spacing: 4) {
                            HStack {
                                Spacer()
                                Text(result.primary.isEmpty ? "(empty)" : result.primary)
                                    .font(.system(.title2, design: .monospaced))
                                    .foregroundStyle(result.isError ? .red : .primary)
                                    .textSelection(.enabled)
                            }
                            .contentShape(Rectangle())
                            .onTapGesture {
                                dismissKeyboard()
                                showingResultDetails = true
                            }
                            if let secondary = result.secondary {
                                Text(secondary).font(.system(.body, design: .monospaced)).foregroundStyle(.secondary).textSelection(.enabled)
                                    .contentShape(Rectangle())
                                    .onTapGesture {
                                        dismissKeyboard()
                                        showingResultDetails = true
                                    }
                            }
                        }
                        .padding(.horizontal)
                        .padding(.bottom, 12)

                        if result.isPlot {
                            PlotCanvas(
                                items: store.plotItems(),
                                showGrid: true,
                                scale: 1,
                                pan: .zero,
                                rotation: CGSize(width: 0.62, height: -0.72),
                                compact: true
                            )
                            .frame(height: 170)
                            .padding(.horizontal)
                            .padding(.bottom, 12)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                dismissKeyboard()
                                showingPlot = true
                            }
                        }
                    }
                }
                .frame(maxHeight: .infinity, alignment: .top)
                .background {
                    Color(uiColor: .secondarySystemBackground)
                        .contentShape(Rectangle())
                        .onTapGesture { dismissKeyboard() }
                }
                
                Divider()
                
                // Extra Sub-panel Toggles
                HStack(spacing: 12) {
                    extraTabButton(title: "Vars", tab: .vars)
                    extraTabButton(title: "Funcs", tab: .funcs)
                    extraTabButton(title: "CAS", tab: .cas)
                }
                .padding(.vertical, 8)
                .padding(.horizontal)
                
                // Sub-panel content
                if activeTab != .none {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            let items = activeTab == .vars ? variableButtons : (activeTab == .funcs ? functionButtons : casButtons)
                            ForEach(items, id: \.self) { item in
                                Button(item) { insert(item == "→" ? "->" : item) }
                                    .buttonStyle(.bordered)
                            }
                        }
                        .padding(.horizontal)
                    }
                    .padding(.bottom, 8)
                }
                
                // Keypad Area
                VStack(spacing: 8) {
                    ForEach(keypadLayout, id: \.self) { row in
                        HStack(spacing: 8) {
                            ForEach(row, id: \.self) { key in
                                keypadButton(key)
                            }
                        }
                    }
                }
                .padding()
                .background(Color(uiColor: .systemBackground))
                .frame(maxHeight: 360)
            }
            .navigationTitle(LocalizedStringKey("Calcora"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .topBarLeading) {
                    Button { showingDebug.toggle() } label: { Image(systemName: "chevron.left.forwardslash.chevron.right") }
                        .accessibilityLabel("Debug input")
                    Button { showingHistory = true } label: { Image(systemName: "clock.arrow.circlepath") }.accessibilityLabel(LocalizedStringKey("History"))
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { showingNaturalMath = true } label: { Image(systemName: "f.cursive") }.accessibilityLabel(LocalizedStringKey("Natural Math"))
                    Button { showingTerminal = true } label: { Image(systemName: "terminal") }.accessibilityLabel(LocalizedStringKey("CAS Terminal"))
                    Button { showingScript = true } label: { Image(systemName: "doc.text") }.accessibilityLabel(LocalizedStringKey("Script Editor"))
                }
            }
            .sheet(isPresented: $showingHistory) {
                HistoryView()
                    .presentationDetents([.medium, .large])
                    .presentationDragIndicator(.visible)
            }
            .sheet(isPresented: $showingTerminal) { TerminalView() }
            .sheet(isPresented: $showingScript) { ScriptView() }
            .sheet(isPresented: $showingNaturalMath) { NaturalMathEditorView(initialText: store.expression).environmentObject(store) }
            .sheet(isPresented: $showingResultDetails) { if let result = store.result { ResultDetailView(result: result) } }
            .sheet(isPresented: $showingPlot) {
                if let result = store.result {
                    PlotView(items: store.plotItems())
                        .environmentObject(store)
                }
            }
            // If there's plot data, maybe a separate launch logic, omitted here or we use a toolbar button if result.isPlot
        }
    }
    
    private func extraTabButton(title: String, tab: ExtraTab) -> some View {
        Button {
            withAnimation { activeTab = activeTab == tab ? .none : tab }
        } label: {
            Text(LocalizedStringKey(title))
                .font(.subheadline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
        }
        .buttonStyle(.borderedProminent)
        .tint(activeTab == tab ? .accentColor : .secondary.opacity(0.2))
        .foregroundStyle(activeTab == tab ? .white : .primary)
    }

    private func keypadButton(_ key: String) -> some View {
        Button {
            handleKey(key)
        } label: {
            Text(key)
                .font(.system(.title2))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .buttonStyle(.bordered)
        .tint(keyColor(for: key))
        .frame(maxWidth: .infinity, minHeight: 48, maxHeight: 62)
    }
    
    private func keyColor(for key: String) -> Color {
        switch key {
        case "AC", "⌫": return .red
        case "=", "()", "^", "÷", "×", "−", "+": return .accentColor
        default: return .secondary.opacity(0.2)
        }
    }

    private func handleKey(_ key: String) {
        switch key {
        case "AC":
            store.expression = ""
            selectedRange = NSRange(location: 0, length: 0)
        case "⌫":
            backspace()
        case "()":
            insertSmartParentheses()
        case "=":
            store.evaluate()
        case "÷": insert("/")
        case "×": insert("*")
        case "−": insert("-")
        case "π": insert("pi")
        default:
            insert(key)
        }
    }

    private func insert(_ token: String) {
        let range = selectedRange ?? NSRange(location: (store.expression as NSString).length, length: 0)
        let offset = token.firstIndex(of: "□").map { token.distance(from: token.startIndex, to: $0) } ?? (token as NSString).length
        guard let stringRange = Range(range, in: store.expression) else {
            store.expression += token
            selectedRange = NSRange(location: (store.expression as NSString).length - (token as NSString).length + offset, length: token.contains("□") ? 1 : 0)
            return
        }
        store.expression.replaceSubrange(stringRange, with: token)
        selectedRange = NSRange(location: range.location + offset, length: token.contains("□") ? 1 : 0)
    }

    private func insertSmartParentheses() {
        let range = selectedRange ?? NSRange(location: (store.expression as NSString).length, length: 0)
        guard let stringRange = Range(range, in: store.expression) else {
            store.expression += "()"
            selectedRange = NSRange(location: (store.expression as NSString).length - 1, length: 0)
            return
        }
        if range.length > 0 {
            store.expression.replaceSubrange(stringRange, with: "(\(store.expression[stringRange]))")
            selectedRange = NSRange(location: range.location + 1, length: range.length)
        } else {
            store.expression.replaceSubrange(stringRange, with: "()")
            selectedRange = NSRange(location: range.location + 1, length: 0)
        }
    }

    private func backspace() {
        let length = (store.expression as NSString).length
        var range = selectedRange ?? NSRange(location: length, length: 0)
        if range.length == 0 && range.location > 0 { range.location -= 1; range.length = 1 }
        guard range.length > 0, let stringRange = Range(range, in: store.expression) else { return }
        store.expression.removeSubrange(stringRange)
        selectedRange = NSRange(location: range.location, length: 0)
    }

    private func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}
