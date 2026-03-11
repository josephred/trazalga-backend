# Trazalga Backend API

Backend API REST para el proyecto Trazalga, desarrollado con Spring Boot 3 y Java 21.

## 🚀 Requisitos

*   **Java**: JDK 21
*   **Base de datos**: MySQL 8.0+
*   **Gestor de dependencias**: Maven (Wrapper incluido)

## 🛠️ Instalación y Configuración

1.  **Clonar el repositorio:**
    ```bash
    git clone https://gitlab.com/aaron.josephred/trazalga_backend_03.git
    cd trazalga_backend_03
    ```

2.  **Configuración de Base de Datos:**
    Asegúrate de tener creadas las bases de datos y usuarios correspondientes según los archivos `application-dev.properties` y `application-prod.properties`.

3.  **Compilación:**
    ```bash
    ./mvnw clean package -DskipTests
    ```

## 🌐 Ambientes y Perfiles (Spring Profiles)

El proyecto utiliza perfiles de Spring para manejar los diferentes servidores:

### 1. Desarrollo / QA (Servidor .210)
*   **Perfil**: `dev` (Perfil por defecto)
*   **Base de datos**: `localhost:3306/trazalga`
*   **Logs**: Nivel DEBUG, guardados en `logs/trazalga_dev.log`
*   **Ejecución**:
    ```bash
    ./mvnw spring-boot:run
    ```

### 2. Producción (Servidor .199)
*   **Perfil**: `prod`
*   **Base de datos**: Producción .199
*   **Logs**: Nivel WARN/INFO, guardados en `logs/trazalga_prod.log`
*   **Ejecución (JAR)**:
    ```bash
    java -jar -Dspring.profiles.active=prod target/api-0.0.1-SNAPSHOT.jar
    ```

## 📄 Documentación Adicional

Para más detalles sobre la instalación desde cero en un servidor Ubuntu nuevo, consulta nuestra guía completa:
👉 [Guía de Despliegue Detallada](.gemini/antigravity/brain/c9dc76d8-3446-49f4-aa14-d4e52135fece/deployment_guide.md)

## 🔐 Seguridad y Certificados

Para generar un certificado autofirmado (si es necesario):
```bash
keytool -genkeypair -alias myserver -keyalg RSA -keysize 2048 -validity 365 -keystore keystore.p12 -storetype PKCS12
```

## 🛰️ Información de Despliegue (SFTP)

Configuración de referencia para despliegue:
```json
{
    "name": "TrazalgaAPI",
    "host": "192.168.100.199",
    "protocol": "sftp",
    "port": 22,
    "username": "manager",
    "remotePath": "/home/manager/trazalga_backend_03"
}
```

mvn clean package -DskipTests
java -jar -Dspring.profiles.active=prod target/api-0.0.1-SNAPSHOT.jar &
 nohup java -jar target/api-0.0.1-SNAPSHOT.jar > salida.log 2>&1 &
 
---
*Desarrollado para Procesac 2024.*