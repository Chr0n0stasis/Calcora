package androidx.annotation

@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.PROPERTY
)
@Retention(AnnotationRetention.SOURCE)
annotation class StringRes
