package com.trazalga.api.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Pruebas de arquitectura con ArchUnit (TA.1).
 * Verifica la disciplina transaccional en servicios y el desacoplamiento de tareas programadas.
 */
public class ArquitecturaTransaccionalTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.trazalga.api");

    private static final List<String> METODOS_ESCRITURA_PREFIXES = List.of(
            "save", "update", "delete", "cerrar", "reabrir", "marcar",
            "registrar", "bloquear", "liberar", "procesar", "recalcular", "revisar", "barrer"
    );

    @Test
    @DisplayName("TA.1 - Regla 1: Métodos públicos de escritura en servicios deben tener @Transactional(readOnly = false)")
    void regla1_metodosEscrituraDebenSerTransaccionalesEscritura() {
        ArchRule rule = methods()
                .that().arePublic()
                .and().areDeclaredInClassesThat().areAnnotatedWith(Service.class)
                .and().areDeclaredInClassesThat(resideInTargetServices())
                .and(haveWritingPrefix())
                .should(haveTransactionalWrite())
                .as("Todo método público de escritura en servicios clave debe estar anotado con @Transactional(readOnly = false)");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("TA.1 - Regla 2: Ninguna clase @Component de tasks debe depender de repositorios")
    void regla2_tasksNoDebenDependerDeRepositorios() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..tasks..")
                .and().areAnnotatedWith(Component.class)
                .should().dependOnClassesThat().resideInAPackage("..repositories..")
                .as("Ninguna clase @Component del paquete tasks puede depender de clases en repositories (debe delegar a servicios)");

        rule.check(importedClasses);
    }

    private static DescribedPredicate<JavaClass> resideInTargetServices() {
        return new DescribedPredicate<>("pertenece a paquetes de servicios nuevos o es CuotaExtraccionService") {
            @Override
            public boolean test(JavaClass javaClass) {
                String name = javaClass.getName();
                return name.contains(".services.cuotas.")
                        || name.contains(".services.hallazgos.")
                        || name.contains(".services.retencion.")
                        || name.contains(".services.indicadores.")
                        || javaClass.getSimpleName().equals("CuotaExtraccionService");
            }
        };
    }

    private static DescribedPredicate<JavaMethod> haveWritingPrefix() {
        return new DescribedPredicate<>("tiene prefijo de método de escritura") {
            @Override
            public boolean test(JavaMethod method) {
                String methodName = method.getName();
                return METODOS_ESCRITURA_PREFIXES.stream().anyMatch(methodName::startsWith);
            }
        };
    }

    private static ArchCondition<JavaMethod> haveTransactionalWrite() {
        return new ArchCondition<>("tener @Transactional con readOnly = false en el método o en la clase") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean hasWriteTransactional = isMethodTransactionalWrite(method);
                if (!hasWriteTransactional) {
                    String message = String.format("Método %s.%s() no tiene @Transactional(readOnly = false) ni lo hereda de la clase",
                            method.getOwner().getSimpleName(), method.getName());
                    events.add(SimpleConditionEvent.violated(method, message));
                }
            }
        };
    }

    private static boolean isMethodTransactionalWrite(JavaMethod method) {
        if (method.isAnnotatedWith(Transactional.class)) {
            Transactional ann = method.getAnnotationOfType(Transactional.class);
            return !ann.readOnly();
        }
        // Si el método no tiene @Transactional propio, verificar si la clase lo provee
        JavaClass owner = method.getOwner();
        if (owner.isAnnotatedWith(Transactional.class)) {
            Transactional ann = owner.getAnnotationOfType(Transactional.class);
            return !ann.readOnly();
        }
        return false;
    }
}
