package io.fjsn.chirp.internal.util;

import io.fjsn.chirp.ChirpRegistry;
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

public class AnnotationScanner {

    public static void scan(String packageName, ChirpRegistry registry) {
        long startTime = System.currentTimeMillis();
        Reflections reflections = new Reflections(packageName, Scanners.TypesAnnotated);
        ChirpLogger.info("AnnotationScanner: Starting scan for package: " + packageName);

        long currentSegmentStart = System.nanoTime();
        for (Class<?> packetClass :
                reflections.getTypesAnnotatedWith(ChirpPacket.class).stream()
                        .filter(c -> !c.getPackage().getName().contains("shaded"))
                        .collect(Collectors.toList())) {
            ChirpPacket chirpPacketAnnotation = packetClass.getAnnotation(ChirpPacket.class);
            if (chirpPacketAnnotation != null && !chirpPacketAnnotation.scan()) {
                continue;
            }

            try {
                registry.registerPacket(packetClass);
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

        currentSegmentStart = System.nanoTime();
        for (Class<?> converterClass :
                reflections.getTypesAnnotatedWith(ChirpConverter.class).stream()
                        .filter(c -> !c.getPackage().getName().contains("shaded"))
                        .collect(Collectors.toList())) {

            ChirpConverter chirpConverterAnnotation =
                    converterClass.getAnnotation(ChirpConverter.class);
            if (chirpConverterAnnotation != null && !chirpConverterAnnotation.scan()) {
                continue;
            }

            try {
                if (!FieldConverter.class.isAssignableFrom(converterClass)) {
                    throw new IllegalArgumentException(
                            "Converter class "
                                    + converterClass.getName()
                                    + " does not implement FieldConverter");
                }

                Class<?> convertedType = getConverterGenericType(converterClass);
                if (convertedType == null) {
                    throw new IllegalArgumentException(
                            "Cannot determine generic type for converter: "
                                    + converterClass.getName()
                                    + ". Ensure it implements FieldConverter<T>.");
                }

                FieldConverter<?> converterInstance =
                        (FieldConverter<?>) converterClass.getDeclaredConstructor().newInstance();

                registry.registerConverter(convertedType, converterInstance);
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

        currentSegmentStart = System.nanoTime();
        for (Class<?> listenerClass :
                reflections.getTypesAnnotatedWith(ChirpListener.class).stream()
                        .filter(c -> !c.getPackage().getName().contains("shaded"))
                        .collect(Collectors.toList())) {

            ChirpListener chirpListenerAnnotation =
                    listenerClass.getAnnotation(ChirpListener.class);
            if (chirpListenerAnnotation != null && !chirpListenerAnnotation.scan()) {
                continue;
            }

            try {
                Constructor<?> ctor = listenerClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                Object listenerInstance = ctor.newInstance();

                registry.registerListener(listenerInstance);
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

    private static Class<?> getConverterGenericType(Class<?> converterClass) {
        for (Type iface : converterClass.getGenericInterfaces()) {
            if (iface instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) iface;
                if (paramType.getRawType() instanceof Class
                        && FieldConverter.class.isAssignableFrom(
                                (Class<?>) paramType.getRawType())) {
                    Type[] typeArgs = paramType.getActualTypeArguments();
                    if (typeArgs.length == 1) {
                        Type typeArg = typeArgs[0];
                        if (typeArg instanceof Class<?>) {
                            return (Class<?>) typeArg;
                        } else if (typeArg instanceof ParameterizedType) {
                            return (Class<?>) ((ParameterizedType) typeArg).getRawType();
                        }
                    }
                }
            }
        }
        return null;
    }
}
