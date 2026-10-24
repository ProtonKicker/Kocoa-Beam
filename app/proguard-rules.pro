# EventBus-generated implementations are loaded via Class.forName
# in EventBus.java:84, so they must not be renamed or removed.
-keep class ru.ytkab0bp.eventbus.impl.** { *; }
-keep class * implements ru.ytkab0bp.eventbus.EventBusListenerImpl { *; }

# EventBus handler methods MUST NOT be renamed by ProGuard.
# They are invoked by name from generated EventBus code, so renaming = silent breakage.
-keepclassmembers class * {
    @ru.ytkab0bp.eventbus.EventHandler <methods>;
}

# Keep the EventHandler annotation class itself at runtime
-keep @interface ru.ytkab0bp.eventbus.EventHandler {
    *;
}

# Klippy/Moonraker per-slot services are instantiated via Class.forName
# in KlipperInstance.kt (KlippyService_<slot> / MoonrakerService_<slot>).
-keep class ru.ytkab0bp.beamklipper.service.KlippyService_* { *; }
-keep class ru.ytkab0bp.beamklipper.service.MoonrakerService_* { *; }
-keep class ru.ytkab0bp.beamklipper.service.WebService { *; }

# Chaquopy runtime classes and the Python bridge.
-keep class com.chaquo.python.** { *; }
-keep class ru.ytkab0bp.beamklipper.KlipperApp { *; }
-keep class ru.ytkab0bp.beamklipper.KlipperInstance { *; }
-keep class ru.ytkab0bp.beamklipper.KlipperInstance$Companion { *; }

# Keep event classes needed for EventBus reflective lookup
-keep class ru.ytkab0bp.beamklipper.events.** { *; }

# Keep Prefs methods that are referenced by generated EventBus code
-keepclassmembers class ru.ytkab0bp.beamklipper.utils.Prefs {
    public *;
}
