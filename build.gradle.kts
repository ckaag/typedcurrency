import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

plugins {
    kotlin("jvm") version "1.6.20"
    id("org.springframework.boot") version "2.7.5"
    id("io.spring.dependency-management") version "1.0.15.RELEASE"
    `maven-publish`
    `java-library`
    java
}

group = "com.github.ckaag"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    withJavadocJar()
    withSourcesJar()
}

configurations {
    testCompileOnly {
        this.extendsFrom(testAnnotationProcessor.get())
    }
    compileOnly {
        this.extendsFrom(annotationProcessor.get())
    }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.test {
    useJUnitPlatform()
}

sourceSets {
    main {
        java {
            srcDir("build/generated/sources/headers/java")
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

abstract class TypedCurrencyGenerationPluginExtension {
    abstract val scales: ListProperty<Int>
    abstract val currencyCodes: ListProperty<String>
    abstract val includeNullable: Property<Boolean>
    abstract val skipBootAutoConfig: Property<Boolean>

    init {
        scales.convention(IntRange(0, 6).toList())
        currencyCodes.convention(listOf("EUR", "USD"))
        skipBootAutoConfig.convention(false)
        includeNullable.convention(true)
    }
}

class TypedCurrencyGenerationPlugin : Plugin<Project> {
    private var projectDir: File? = null

    // Those two values are the defaults and get simplified names of their types
    private val defaultCurrency: String = "EUR"
    private val defaultScale: Int = 2
    override fun apply(project: Project) {
        this.projectDir = project.projectDir
        // Add the 'greeting' extension object
        val extension =
            project.extensions.create<TypedCurrencyGenerationPluginExtension>("com.github.ckaag.typedcurrency")
        // Add a task that uses configuration from the extension object
        project.task("genTypedCurrency") {
            doFirst {
                generateInterface()
                val generatedClasses = extension.currencyCodes.get().flatMap { currencyCode ->
                    extension.scales.get().flatMap { scale ->
                        listOf(false, true).flatMap { gross ->
                            // currently we cannot set a default value for Jackson null fields, so we can give up here
                            (if (extension.includeNullable.get()) listOf(
                                false,
                                true
                            ) else listOf(false)).map { nullable ->
                                generateType(nullable, scale, currencyCode, gross)
                            }
                        }
                    }
                }
                if (!extension.skipBootAutoConfig.get()) {
                    generateConfig(generatedClasses)
                }
            }
            doLast {
                println("Finished with generation of typed currency types")
            }
        }
    }


    data class Field(var name: String, var type: String, var constant: String? = null) {
        fun formatAsJavaCodeLine(): String {
            return "${if (constant == null) "private" else "public"} ${if (constant != null) "static " else ""}final $type $name${if (constant != null) " = $constant" else ""};"
        }

        fun toJavaCode() = "$type $name"
    }

    data class MethodImplementations(
        var name: String,
        var type: String,
        var parameters: List<Field> = listOf(),
        var implementation: String? = null
    ) {
        fun formatAsJavaCodeLine(): String {
            return "public $type $name(${parameters.joinToString(", ") { it.toJavaCode() }})${if (implementation == null) ";" else " {\n    ${implementation}\n  }"}"
        }
    }

    private fun generateJavaFile(
        simpleName: String,
        prefix: String,
        suffix: String,
        fields: List<Field>,
        methods: List<MethodImplementations>,
        constructors: List<String>
    ) {
        writePlainJavaFile(simpleName,
            """package com.github.ckaag.typedcurrency;
                |
                |import java.util.*;
                |import java.math.*;
                |
                |$prefix $simpleName $suffix {
                |${fields.sortedBy { it.constant == null }.joinToString("\n") { "  " + it.formatAsJavaCodeLine() }}
                |${constructors.joinToString("\n") { "  $it" }}
                |${methods.joinToString("\n") { "  " + it.formatAsJavaCodeLine() }}
                |}
            """.trimMargin()
        )
    }

    private fun writePlainJavaFile(simpleName: String, content: String) {
        val file = File(
            projectDir!!,
            "build/generated/sources/headers/java/com/github/ckaag/typedcurrency/$simpleName.java"
        )
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun generateType(nullable: Boolean, scale: Int, currencyCode: String, gross: Boolean): String {
        val name = getTypeName(currencyCode, scale, gross, nullable)
        val mirroredTypeName = getTypeName(
            currencyCode,
            scale,
            !gross,
            false
        )
        generateJavaFile(
            name,
            "public class",
            "",
            listOf(
                Field("value", "BigDecimal"),
                Field("scale", "int", "$scale"),
                Field("currency", "java.util.Currency", "Currency.getInstance(\"$currencyCode\")"),
                Field("gross", "Boolean", "$gross"),
                Field("allowsNull", "Boolean", "$nullable"),
                Field("ZERO", name, "new $name(BigDecimal.ZERO)"),
            ),
            listOf(
                MethodImplementations("toBigDecimal", "BigDecimal", listOf(), "return value;"),
                MethodImplementations(
                    if (gross) "toNet" else "toGross",
                    mirroredTypeName,
                    listOf(Field("vat", "VATSource")),
                    if (gross) "return new $mirroredTypeName(value.divide(vat.getVatValueMultiplier().add(BigDecimal.ONE), scale, RoundingMode.HALF_UP));" else "return new $mirroredTypeName(value.multiply(vat.getVatValueMultiplier().add(BigDecimal.ONE)));"
                ),
                MethodImplementations(
                    "add",
                    name,
                    listOf(Field("other", name)),
                    "return new $name(toBigDecimal().add(other.toBigDecimal()));"
                ),
                MethodImplementations(
                    "subtract",
                    name,
                    listOf(Field("other", name)),
                    "return new $name(toBigDecimal().subtract(other.toBigDecimal()));"
                ),
                MethodImplementations(
                    "multiply",
                    name,
                    listOf(Field("other", "BigDecimal")),
                    "return new $name(toBigDecimal().multiply(other));"
                ),
                MethodImplementations(
                    "multiply",
                    name,
                    listOf(Field("other", "long")),
                    "return new $name(toBigDecimal().multiply(BigDecimal.valueOf(other)));"
                ),
                MethodImplementations(
                    "multiply",
                    name,
                    listOf(Field("other", "double")),
                    "return new $name(toBigDecimal().multiply(BigDecimal.valueOf(other)));"
                ),
                MethodImplementations(
                    "divide",
                    name,
                    listOf(Field("other", "BigDecimal")),
                    "return new $name(toBigDecimal().multiply(other));"
                ),
                MethodImplementations(
                    "modulo",
                    name,
                    listOf(Field("other", "BigDecimal")),
                    "return new $name(toBigDecimal().remainder(other));"
                ),
                MethodImplementations(
                    "modulo",
                    name,
                    listOf(Field("other", name)),
                    "return new $name(toBigDecimal().remainder(other.toBigDecimal()));"
                ),
            ),
            listOf(
                "public $name(BigDecimal bd) {this.value = bd.setScale(scale, java.math.RoundingMode.HALF_UP);}",
                "public $name(Long l) {this(new BigDecimal(l));}",
                "public $name(Double d) {this(new BigDecimal(d));}",
                "public $name(String s) {this(new BigDecimal(s));}",
            )
        )
        return name;
    }

    private fun getTypeName(
        currencyCode: String,
        scale: Int,
        gross: Boolean,
        nullable: Boolean
    ): String {
        val name = "Currency${
            if (defaultCurrency === currencyCode) "" else
                currencyCode.toLowerCaseAsciiOnly().capitalizeAsciiOnly()
        }${if (defaultScale == scale) "" else "S$scale"}${if (gross) "Gross" else "Net"}${if (nullable) "Nullable" else ""}"
        return name
    }

    data class Method(val returnValue: String = "java.math.BigDecimal", val name: String = "getVatValueMultiplier") {

        fun toNonImplementation(): MethodImplementations = MethodImplementations(name, returnValue)
    }

    private fun generateInterface(name: String = "VATSource", methods: List<Method> = listOf(Method())) {
        generateJavaFile(name, "public interface", "", listOf(), methods.map { it.toNonImplementation() }, listOf())
    }

    private fun generateConfig(generatedClasses: List<String>) {
        println("Generated following classes:")
        generatedClasses.forEach {
            println(" - $it")
        }
//TODO: reuse same class to minimize metaspace

        val prefix = """package com.github.ckaag.typedcurrency;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;

@Configuration
public class TypedCurrencyConfig {

    @Bean
    public com.fasterxml.jackson.databind.Module typedCurrencySerializerModule() {
        SimpleModule module = new SimpleModule();
"""
        val suffix = """
        return module;
    }
}
"""

        writePlainJavaFile("TypedCurrencyConfig", prefix + generatedClasses.joinToString("\n") {
            buildSerializerDeserializerForType(
                it
            )
        } + suffix)
    }

    private fun buildSerializerDeserializerForType(simpleClassName: String) = """
        module.addSerializer($simpleClassName.class, new StdSerializer<>($simpleClassName.class) {
            @Override
            public void serialize($simpleClassName value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                if (value == null) {
                    gen.writeNull();
                } else {
                    gen.writeString(value.toBigDecimal().toString());
                }
            }
        });
        module.addDeserializer($simpleClassName.class, new StdDeserializer<>($simpleClassName.class) {
            @Override
            public $simpleClassName deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                switch (p.currentTokenId()) {
                    case JsonTokenId.ID_NO_TOKEN:
                    case JsonTokenId.ID_NULL:
                        if ($simpleClassName.allowsNull) {
                            return null;
                        } else {
                            return new $simpleClassName(BigDecimal.ZERO);
                        }
                    case JsonTokenId.ID_NUMBER_INT:
                    case JsonTokenId.ID_NUMBER_FLOAT:
                        return new $simpleClassName(p.getDecimalValue());
                    case JsonTokenId.ID_STRING:
                        String text = p.getText();
                        if ("null".equals(text)) {
                            if ($simpleClassName.allowsNull) {
                                return null;
                            } else {
                                return new $simpleClassName(BigDecimal.ZERO);
                            }
                        } else {
                            return new $simpleClassName(text);
                        }
                    default:
                        return ($simpleClassName) ctxt.handleWeirdStringValue(_valueClass, p.getText(), "not a valid representation");
                }
            }
        });"""
}

apply<TypedCurrencyGenerationPlugin>()

tasks.compileJava {
    dependsOn("genTypedCurrency")
}
tasks.testClasses {
    dependsOn("genTypedCurrency")
}