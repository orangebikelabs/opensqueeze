-dontwarn java.beans.Transient
-dontwarn java.beans.ConstructorProperties
-dontwarn java.nio.file.Paths
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry

# don't obfuscate Jackson classes
-keep class com.fasterxml.** { *; }
-keep class org.codehaus.** { *; }
-keep @com.fasterxml.jackson.annotation.JsonIgnoreProperties class * { *; }
-keep @com.fasterxml.jackson.annotation.JsonCreator class * { *; }
-keep @com.fasterxml.jackson.annotation.JsonValue class * { *; }

-keepnames class com.fasterxml.** { *; }

-keepclassmembers class ** {
     @com.fasterxml.jackson.annotation.JsonCreator *;
     @com.fasterxml.jackson.annotation.JsonProperty *;
}
-keepclassmembers public final enum com.fasterxml.jackson.annotation.JsonAutoDetect$Visibility {
    public static final com.fasterxml.jackson.annotation.JsonAutoDetect$Visibility *;
}

# don't strip runtime annotations
-keepattributes RuntimeVisibleAnnotations,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod,Signature,Exceptions,InnerClasses