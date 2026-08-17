import SwiftUI
import UIKit

struct CalculatorView: View {
    @EnvironmentObject private var store: CalcoraStore
    @State private var selectedRange: NSRange?
    @State private var showingTerminal = false
    @State private var showingScript = false
    @State private var showingPlot = false
    @State private var showingResultDetails = false
    @State private var showingHistory = false
    @State private var showingDebug = false
    @State private var activeTab: ExtraTab = .none

    enum ExtraTab {
        case none, vars, funcs, cas
    }

    private let functionButtons = [
        "sin()", "cos()", "tan()", "asin()", "acos()", "atan()",
        "sqrt()", "log()", "ln()", "exp()", "abs()", "floor()", "ceil()",
        "^", "integrate(,x)", "integrate(,x,0,1)", "diff(,x)", "limit(,x=0)", "sum(,k,1,n)",
        "plot(,x=-5..5)", "plot3d(,x=-5..5,y=-5..5)"
    ]
    private let casButtons = [
        "solve(=0,x)", "factor()", "expand()", "normal()",
        "subst(,x=)", "diff(,x)", "diff(,x,2)",
        "integrate(,x)", "integrate(,x,0,1)", "limit(,x=0)",
        "sum(,k,1,n)", "det()", "inv()", "transpose()", "rank()",
        "gcd(,)", "lcm(,)", "ifactor()", "simplify()",
        "plot(,x=-5..5)", "plot3d(,x=-5..5,y=-5..5)", "plotparam(,t)",
        "makelist(,k,1,n)", "makemat(,n,p)", "fft()", "ifft()", "help()"
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
        ["0", ".", ",", "EXE"]
    ]

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                VStack(spacing: 0) {
                // Expression and Result Area
                VStack(alignment: .leading, spacing: 0) {
                    if showingDebug {
                        HStack(spacing: 6) {
                            Text(LocalizedStringKey("Debug expression"))
                            Text(ExpressionFormatter.toEngineInput(store.expression))
                                .textSelection(.enabled)
                        }
                        .font(.system(.caption, design: .monospaced))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal)
                        .padding(.bottom, 8)
                        .transition(.opacity.combined(with: .move(edge: .top)))
                    }

                    if store.settings.autocompleteEnabled, !store.autocompleteSuggestions.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(store.autocompleteSuggestions, id: \.self) { suggestion in
                                    Button(suggestion) { applyAutocomplete(suggestion) }
                                        .font(.system(.caption, design: .monospaced))
                                        .buttonStyle(.bordered)
                                }
                            }
                            .padding(.horizontal)
                        }
                        .padding(.bottom, 8)
                    }

                    NaturalMathInputView(
                        text: $store.expression,
                        selectedRange: $selectedRange,
                        fontSize: 28,
                        onCommit: { effectiveExpression in
                            store.expression = effectiveExpression
                            store.evaluate()
                            if store.result?.isPlot == true { DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { withAnimation { proxy.scrollTo("plot_bottom", anchor: .bottom) } } }
                        }
                    )
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
                            .frame(height: 110)
                            .id("plot_bottom")
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
                .padding(.horizontal, 12)
                .padding(.top, 8)
                .frame(maxHeight: .infinity, alignment: .top)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
                .contentShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                .onTapGesture { dismissKeyboard() }
                
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
                                Button(item) { tapFeedback(); insert(item == "→" ? "->" : item) }
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
                .padding(12)
                .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
                .padding(.horizontal, 12)
                .padding(.bottom, 12)
                .frame(maxHeight: 360)
            }
            .background(.ultraThinMaterial)
            .ignoresSafeArea(.keyboard, edges: .bottom)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Button { dismissKeyboard() } label: {
                        Text(LocalizedStringKey("Calcora"))
                            .font(.headline)
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint(Text(LocalizedStringKey("Dismiss keyboard")))
                }
                ToolbarItemGroup(placement: .topBarLeading) {
                    Button { showingDebug.toggle() } label: { Image(systemName: "chevron.left.forwardslash.chevron.right") }
                        .accessibilityLabel(LocalizedStringKey("Debug input"))
                    Button { showingHistory = true } label: { Image(systemName: "clock.arrow.circlepath") }.accessibilityLabel(LocalizedStringKey("History"))
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Menu {
                        ForEach(EvalMode.allCases) { mode in
                            Button {
                                store.selectedMode = mode
                            } label: {
                                if store.selectedMode == mode {
                                    Label(LocalizedStringKey(mode.rawValue), systemImage: "checkmark")
                                } else {
                                    Text(LocalizedStringKey(mode.rawValue))
                                }
                            }
                        }
                    } label: {
                        Image(systemName: "slider.horizontal.3")
                    }
                    .accessibilityLabel(LocalizedStringKey("Mode"))
                    Button { showingTerminal = true } label: { Image(systemName: "terminal") }.accessibilityLabel(LocalizedStringKey("CAS Terminal"))
                    Button { showingScript = true } label: { Image(systemName: "doc.text") }.accessibilityLabel(LocalizedStringKey("Script Editor"))
                }
            }
                }
            } // ScrollViewReader
            .sheet(isPresented: $showingHistory) {
                HistoryView()
                    .presentationDetents([.medium, .large])
                    .presentationDragIndicator(.visible)
            }
            .sheet(isPresented: $showingTerminal) { TerminalView() }
            .sheet(isPresented: $showingScript) { ScriptView() }
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
        .buttonBorderShape(.roundedRectangle(radius: 14))
        .tint(activeTab == tab ? .accentColor : .secondary.opacity(0.2))
        .foregroundStyle(activeTab == tab ? .white : .primary)
    }

    private func keypadButton(_ key: String) -> some View {
        Button {
            tapFeedback()
            handleKey(key)
        } label: {
            Text(key)
                .font(.system(.title2))
                .foregroundStyle(keyForeground(for: key))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .buttonStyle(.plain)
        .background(keyBackground(for: key), in: RoundedRectangle(cornerRadius: 15, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 15, style: .continuous).stroke(.primary.opacity(keyRole(for: key) == .number ? 0.08 : 0.16), lineWidth: 1))
        .shadow(color: .black.opacity(keyRole(for: key) == .execute ? 0.22 : 0.08), radius: keyRole(for: key) == .execute ? 7 : 3, y: keyRole(for: key) == .execute ? 4 : 2)
        .frame(maxWidth: .infinity, minHeight: 48, maxHeight: 62)
        .onLongPressGesture(minimumDuration: 0.45) {
            handleLongPress(key)
        }
        .contextMenu {
            longPressMenu(for: key)
        }
    }

    private enum KeyRole { case number, clear, backspace, mathOperator, equals, execute }

    private func keyRole(for key: String) -> KeyRole {
        switch key {
        case "AC": return .clear
        case "⌫": return .backspace
        case "EXE": return .execute
        case "=": return .equals
        case "()", "^", "÷", "×", "−", "+": return .mathOperator
        default: return .number
        }
    }

    private func keyBackground(for key: String) -> Color {
        switch key {
        case "AC": return .red.opacity(0.18)
        case "⌫": return Color(uiColor: .secondarySystemGroupedBackground)
        case "EXE", "=": return .accentColor
        case "()", "^", "÷", "×", "−", "+": return Color.accentColor.opacity(0.16)
        default: return Color(uiColor: .secondarySystemGroupedBackground).opacity(0.92)
        }
    }

    private func keyForeground(for key: String) -> Color {
        switch keyRole(for: key) {
        case .clear: return .red
        case .execute, .equals: return .white
        case .mathOperator: return .accentColor
        case .number, .backspace: return .primary
        }
    }

    private func handleKey(_ key: String) {
        switch key {
        case "AC":
            store.expression = ""
            store.result = nil
            showingPlot = false
            selectedRange = NSRange(location: 0, length: 0)
        case "⌫":
            backspace()
        case "()":
            insertSmartParentheses()
        case "=":
            insert("=")
        case "EXE":
            store.evaluate()
        case ",":
            insert(",")
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
        let offset = token.firstIndex(of: "(").map { token.distance(from: token.startIndex, to: $0) + 1 } ?? (token as NSString).length
        guard let stringRange = Range(range, in: store.expression) else {
            store.expression += token
            selectedRange = NSRange(location: (store.expression as NSString).length - (token as NSString).length + offset, length: 0)
            return
        }
        store.expression.replaceSubrange(stringRange, with: token)
        selectedRange = NSRange(location: range.location + offset, length: 0)
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

    private func currentWordRange() -> NSRange? {
        let ns = store.expression as NSString
        let cursor = min(selectedRange?.location ?? ns.length, ns.length)
        var start = cursor
        while start > 0 {
            let unit = ns.character(at: start - 1)
            let isWord = (unit >= 48 && unit <= 57) || (unit >= 65 && unit <= 90) || (unit >= 97 && unit <= 122) || unit == 95
            guard isWord else { break }
            start -= 1
        }
        guard start < cursor else { return nil }
        return NSRange(location: start, length: cursor - start)
    }

    private func applyAutocomplete(_ suggestion: String) {
        let ns = store.expression as NSString
        let range = currentWordRange() ?? NSRange(location: ns.length, length: 0)
        guard let swiftRange = Range(range, in: store.expression) else { return }
        store.expression.replaceSubrange(swiftRange, with: suggestion)
        selectedRange = NSRange(location: range.location + (suggestion as NSString).length, length: 0)
    }

    private func handleLongPress(_ key: String) {
        tapFeedback(style: .medium)
        switch key {
        case "EXE": insert("=")
        case "×": insert("^()")
        case "7": insert("x")
        case "8": insert("y")
        case "9": insert("z")
        case "3": insert("e")
        case "⌫": deletePreviousOperand()
        default: break
        }
    }

    @ViewBuilder
    private func longPressMenu(for key: String) -> some View {
        switch key {
        case "EXE":
            Button(LocalizedStringKey("Insert equals sign")) { insert("=") }
        case "×":
            Button("^()") { insert("^()") }
        case "7":
            Button("x") { insert("x") }
        case "8":
            Button("y") { insert("y") }
        case "9":
            Button("z") { insert("z") }
        case "3":
            Button("e") { insert("e") }
        case "()":
            Button("(") { insert("(") }
            Button(")") { insert(")") }
        case "⌫":
            Button(LocalizedStringKey("Clear previous operand"), role: .destructive) { deletePreviousOperand() }
        default:
            EmptyView()
        }
    }

    private func deletePreviousOperand() {
        let ns = store.expression as NSString
        let cursor = min(selectedRange?.location ?? ns.length, ns.length)
        var index = max(0, cursor - 1)
        while index > 0 {
            let unit = ns.character(at: index)
            let isOperator = [40, 41, 43, 45, 42, 47, 94, 44].contains(Int(unit))
            if isOperator { break }
            index -= 1
        }
        let range = NSRange(location: index, length: max(0, cursor - index))
        guard range.length > 0, let swiftRange = Range(range, in: store.expression) else { return }
        store.expression.removeSubrange(swiftRange)
        selectedRange = NSRange(location: index, length: 0)
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

    private func tapFeedback(style: UIImpactFeedbackGenerator.FeedbackStyle = .light) {
        UIImpactFeedbackGenerator(style: style).impactOccurred()
    }
}


