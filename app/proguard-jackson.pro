-dontwarn java.beans.Transient
-dontwarn java.beans.ConstructorProperties
-dontwarn java.nio.file.Paths
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry

-keepclassmembers class ** {
     @com.fasterxml.jackson.annotation.JsonCreator *;
     @com.fasterxml.jackson.annotation.JsonProperty *;
}
-keepclassmembers public final enum com.fasterxml.jackson.annotation.JsonAutoDetect$Visibility {
    public static final com.fasterxml.jackson.annotation.JsonAutoDetect$Visibility *;
}
-keep, allowobfuscation, allowoptimization class com.fasterxml.jackson.core.type.TypeReference
-keep, allowobfuscation, allowoptimization class * extends com.fasterxml.jackson.core.type.TypeReference

# don't strip runtime annotations
-keepattributes RuntimeVisibleAnnotations,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod,Signature,Exceptions,InnerClasses