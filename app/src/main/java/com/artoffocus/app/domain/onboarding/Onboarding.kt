package com.artoffocus.app.domain.onboarding

data class Slide(val title: String, val body: String)

/** F12.1 — premise-only slides, never a feature tour. */
val SLIDES: List<Slide> = listOf(
    Slide("Focus becomes art", "Every stretch of deep work uncovers one piece of a hidden painting."),
    Slide(
        "You choose the shape",
        "Pick how long the whole painting takes, then how long one sitting runs. The pieces follow.",
    ),
    Slide(
        "Finished work is kept",
        "Completed paintings move permanently into your Museum. Nothing you earn is ever lost.",
    ),
)

enum class OnboardingStep { SLIDES, SETUP, DONE }

data class OnboardingState(val step: OnboardingStep = OnboardingStep.SLIDES, val slideIndex: Int = 0)

fun nextSlide(state: OnboardingState): OnboardingState {
    if (state.step != OnboardingStep.SLIDES) return state
    return if (state.slideIndex >= SLIDES.lastIndex) {
        state.copy(step = OnboardingStep.SETUP)
    } else {
        state.copy(slideIndex = state.slideIndex + 1)
    }
}

fun previousSlide(state: OnboardingState): OnboardingState {
    if (state.step != OnboardingStep.SLIDES || state.slideIndex == 0) return state
    return state.copy(slideIndex = state.slideIndex - 1)
}

/** F12.2 — skipping counts as completing; it jumps straight to DONE, not SETUP. */
fun skipOnboarding(): OnboardingState = OnboardingState(step = OnboardingStep.DONE)

/** F12.1 — the last slide's button reads "Continue", not "Next". */
fun isLastSlide(state: OnboardingState): Boolean = state.slideIndex >= SLIDES.lastIndex

/** Finishing the identity step completes onboarding exactly as skipping does (F12.2). */
fun completeSetup(): OnboardingState = OnboardingState(step = OnboardingStep.DONE)

fun isComplete(state: OnboardingState): Boolean = state.step == OnboardingStep.DONE
