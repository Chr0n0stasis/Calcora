import SwiftUI

struct SwipeModifier: ViewModifier {
    let onLeft: () -> Void
    let onRight: () -> Void

    func body(content: Content) -> some View {
        content.simultaneousGesture(
            DragGesture(minimumDistance: 15)
                .onEnded { value in
                    if value.translation.width < -15 {
                        onLeft()
                    } else if value.translation.width > 15 {
                        onRight()
                    }
                }
        )
    }
}

extension View {
    func onHorizontalSwipe(onLeft: @escaping () -> Void = {}, onRight: @escaping () -> Void = {}) -> some View {
        self.modifier(SwipeModifier(onLeft: onLeft, onRight: onRight))
    }
}
