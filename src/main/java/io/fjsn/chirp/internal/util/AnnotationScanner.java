package io.fjsn.chirp.internal.util;

import io.fjsn.chirp.ChirpRegistry; // Needs to call methods on ChirpRegistry
import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.annotation.ChirpListener;
import io.fjsn.chirp.annotation.ChirpPacket;
import io.fjsn.chirp.converter.FieldConverter;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.stream.Collectors;

/**
 * Utility class responsible for scanning packages for Chirp-related annotations ({@link
 * ChirpPacket}, {@link ChirpConverter}, {@link ChirpListener}) and initiating their registration in
 * the provided {@link ChirpRegistry}.
 */
public class AnnotationScanner {

    public static void scan(String packageName, ChirpRegistry registry) {
        long startTime = System.currentTimeMillis();
        // Initialize Reflections to scan for types annotated with specific annotations
        Reflections reflections = new Reflections(packageName, Scanners.TypesAnnotated);
        ChirpLogger.info("AnnotationScanner: Starting scan for package: " + packageName);

        // Phase 1: Scan and register ChirpPacket classes
        long currentSegmentStart = System.nanoTime();
        for (Class<?> packetClass :
                reflections.getTypesAnnotatedWith(ChirpPacket.class).stream()
                        .filter(
                                c ->
                                        !c.getPackage()
                                                .getName()
                                                .contains(
                                                        "shaded")) // Avoid scanning shaded classes
                        .collect(Collectors.toList())) {
            ChirpPacket chirpPacketAnnotation = packetClass.getAnnotation(ChirpPacket.class);
            if (chirpPacketAnnotation != null && !chirpPacketAnnotation.scan()) {
                // If scan() is explicitly set to false on the annotation, skip this class
                continue;
            }

            try {
                registry.registerPacket(packetClass); // Delegate registration to ChirpRegistry
            } catch (IllegalArgumentException e) {
                throw new RuntimeException(
                        "AnnotationScanner: Failed to register packet during scan: "
                                + packetClass.getName()
                                + " - "
                                + e.getMessage(),
                        e);
            }
        }
        long packetScanTime = System.nanoTime() - currentSegmentStart;
        ChirpLogger.debug(
                "AnnotationScanner: Packet scanning phase completed in "
                        + packetScanTime / 1_000_000.0
                        + "ms.");

        // Phase 2: Scan and register ChirpConverter classes
        currentSegmentStart = System.nanoTime();
        for (Class<?> converterClass :
                reflections.getTypesAnnotatedWith(ChirpConverter.class).stream()
                        .filter(
                                c ->
                                        !c.getPackage()
                                                .getName()
                                                .contains(
                                                        "shaded")) // Avoid scanning shaded classes
                        .collect(Collectors.toList())) {

            ChirpConverter chirpConverterAnnotation =
                    converterClass.getAnnotation(ChirpConverter.class);
            if (chirpConverterAnnotation != null && !chirpConverterAnnotation.scan()) {
                // If scan() is explicitly set to false on the annotation, skip this class
                continue;
            }

            try {
                if (!FieldConverter.class.isAssignableFrom(converterClass)) {
                    throw new IllegalArgumentException(
                            "Converter class "
                                    + converterClass.getName()
                                    + " does not implement FieldConverter");
                }

                // Determine the generic type argument the converter handles
                Class<?> convertedType = getConverterGenericType(converterClass);
                if (convertedType == null) {
                    throw new IllegalArgumentException(
                            "Cannot determine generic type for converter: "
                                    + converterClass.getName()
                                    + ". Ensure it implements FieldConverter<T>.");
                }

                // Instantiate the converter using its no-argument constructor
                FieldConverter<?> converterInstance =
                        (FieldConverter<?>) converterClass.getDeclaredConstructor().newInstance();

                registry.registerConverter(
                        convertedType, converterInstance); // Delegate registration
            } catch (Exception e) {
                throw new RuntimeException(
                        "AnnotationScanner: Failed to register converter during scan: "
                                + converterClass.getName()
                                + " - "
                                + e.getMessage(),
                        e);
            }
        }
        long converterScanTime = System.nanoTime() - currentSegmentStart;
        ChirpLogger.debug(
                "AnnotationScanner: Converter scanning phase completed in "
                        + converterScanTime / 1_000_000.0
                        + "ms.");

        // Phase 3: Scan and register ChirpListener classes
        currentSegmentStart = System.nanoTime();
        for (Class<?> listenerClass :
                reflections.getTypesAnnotatedWith(ChirpListener.class).stream()
                        .filter(
                                c ->
                                        !c.getPackage()
                                                .getName()
                                                .contains(
                                                        "shaded")) // Avoid scanning shaded classes
                        .collect(Collectors.toList())) {

            ChirpListener chirpListenerAnnotation =
                    listenerClass.getAnnotation(ChirpListener.class);
            if (chirpListenerAnnotation != null && !chirpListenerAnnotation.scan()) {
                // If scan() is explicitly set to false on the annotation, skip this class
                continue;
            }

            try {
                // Instantiate the listener using its no-argument constructor
                Constructor<?> ctor = listenerClass.getDeclaredConstructor();
                ctor.setAccessible(true); // Allow access to private constructors
                Object listenerInstance = ctor.newInstance();

                registry.registerListener(listenerInstance); // Delegate registration
            } catch (Exception e) {
                throw new RuntimeException(
                        "AnnotationScanner: Failed to register listener during scan: "
                                + listenerClass.getName()
                                + " - "
                                + e.getMessage(),
                        e);
            }
        }
        long listenerScanTime = System.nanoTime() - currentSegmentStart;
        ChirpLogger.debug(
                "AnnotationScanner: Listener scanning phase completed in "
                        + listenerScanTime / 1_000_000.0
                        + "ms.");

        long endTime = System.currentTimeMillis();
        ChirpLogger.info(
                "AnnotationScanner: Overall scan for package: "
                        + packageName
                        + " completed in "
                        + (endTime - startTime)
                        + "ms.");
    }

    /**
     * Helper method to determine the generic type argument of a {@link FieldConverter}
     * implementation. This is crucial for correctly registering custom converters.
     *
     * @param converterClass The {@link Class} of the {@link FieldConverter} implementation.
     * @return The {@link Class} representing the generic type the converter handles, or null if it
     *     cannot be determined.
     */
    private static Class<?> getConverterGenericType(Class<?> converterClass) {
        for (Type iface : converterClass.getGenericInterfaces()) {
            if (iface instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) iface;
                // Check if the raw type of the interface is assignable from FieldConverter
                if (paramType.getRawType() instanceof Class
                        && FieldConverter.class.isAssignableFrom(
                                (Class<?>) paramType.getRawType())) {
                    Type[] typeArgs = paramType.getActualTypeArguments();
                    if (typeArgs.length == 1) {
                        Type typeArg = typeArgs[0];
                        if (typeArg instanceof Class<?>) {
                            return (Class<?>) typeArg; // Direct class (e.g., String)
                        } else if (typeArg instanceof ParameterizedType) {
                            // Handles cases like FieldConverter<List<String>> - returns List.class
                            return (Class<?>) ((ParameterizedType) typeArg).getRawType();
                        }
                    }
                }
            }
        }
        return null;
    }
}
