# TotalNet Bootstrap — Plugin para NetBeans

Plugin de NetBeans para montar el proyecto **TotalNet** desde Git de forma automática:
clona la rama, inyecta la configuración que no está versionada (`nbproject`/`build.xml`),
configura los perfiles de Tomcat/BD, compila y abre el proyecto en la instancia actual.

Compatible con **NetBeans 30 y 31** (se compila contra NetBeans 30).

---

## Requisitos

- NetBeans 30 o 31.
- Git instalado y con acceso al repositorio.
- Apache Tomcat 9 (no hace falta instalarlo como servicio; basta la carpeta).
- JDK 17 o superior para ejecutar NetBeans (el proyecto compila a Java 8).

---

## Instalación del plugin

El artefacto listo para instalar está en la raíz del repositorio:

```
TotalNetBootstrap.nbm
```

Pasos:

1. Abre NetBeans.
2. `Tools` → `Plugins` → pestaña **Downloaded** (Descargados).
3. Pulsa **Add Plugins...** (Agregar plugins).
4. Selecciona `TotalNetBootstrap.nbm`.
5. Pulsa **Install** y acepta el aviso de **plugin sin firmar**.
6. Reinicia NetBeans.

Después de reiniciar aparecen en `Tools`:

- **TotalNet → Gestionar perfiles y aplicar TotalNet**
- **TotalNet → Montar proyecto TotalNet...**

---

## Uso rápido

### 1. Crear un perfil de entorno

`Tools` → `TotalNet` → **Gestionar perfiles y aplicar TotalNet**

1. Pulsa **Nuevo**.
2. Escribe un **nombre de perfil** (ej. `Desarrollo local`).
3. Selecciona la carpeta de **Tomcat (home)** con el botón `...`.
   - Si no tienes Tomcat, pulsa el icono **ⓘ** al lado: indica cómo descargar Tomcat 9 y qué carpeta seleccionar.
4. Completa las variables **básicas**:
   - `TOTALNET_DB_IP` → formato `host:puerto/` (termina en `/`).
   - `TOTALNET_DB_NAME`
   - `TOTALNET_DB_USER`
   - `TOTALNET_DB_PASSWORD` (si se deja vacía, se conserva la que ya esté en el `setenv`).
   - `TOTALNET_DB_DRIVER` → `org.postgresql.Driver`.
5. Para variables adicionales usa la tabla **Otras variables (opcional)**:
   - **Agregar variable** / **Quitar seleccionada**.
   - Ejemplos: `TOTALNET_DB_URL` (`jdbc:postgresql://`), `TOTALNET_COD_ENTORNO`,
     `TOTALNET_ARCHIVOS_PATH`, `TOTALNET_DB_IP_CAL`, `TOTALNET_DB_NAME_CAL`.
6. Revisa la **vista previa** (muestra exactamente lo que se escribirá en el `setenv`).
7. Pulsa **Guardar perfil**.

### 2. Aplicar un perfil a un proyecto ya abierto

En la misma ventana:

1. Selecciona el perfil en la lista.
2. Pulsa **Aplicar al proyecto abierto**.

Se usa el proyecto que esté abierto en NetBeans; no se pide ruta.
Si no hay proyecto abierto, el plugin avisa.

### 3. Montar un proyecto desde cero

`Tools` → `TotalNet` → **Montar proyecto TotalNet...**

1. **Repositorio Git** (por defecto el de TotalNet).
   - Si el repo es SSH y aparece una ventana pidiendo la clave, ejecuta una vez:
     `ssh-add ~/.ssh/id_rsa`
2. **Carpeta contenedora** (ej. `~/NetBeansProjects`).
3. **Nombre del proyecto** (ej. `mi_proyecto`).  
   El plugin crea la carpeta `~/NetBeansProjects/mi_proyecto`.
4. **Rama**:
   - **Rama existente del repositorio**: desplegable con las ramas reales.
   - **Solo master (sin rama nueva)**.
   - **Rama nueva desde master**.
5. **Perfil de entorno**: selecciona el perfil creado.
   - Si no hay perfiles, el plugin ofrece crearlos antes de montar.
6. Pulsa **Montar y compilar**.

El plugin:

- clona o actualiza el repositorio,
- crea/cambia la rama,
- inyecta `nbproject` y `build.xml`,
- aplica el perfil seleccionado,
- compila con `ant clean dist`,
- abre el proyecto en la **instancia actual** de NetBeans.

> El plugin **solo compila**. El despliegue se hace con **Run** en NetBeans usando el Tomcat ya registrado.

---

## Variables y perfiles

### Dónde se guardan los perfiles

```
~/.totalnet/profiles/<nombre>.properties
```

- Por computadora, fuera del repositorio.
- Permisos `600` en Linux.
- No se versionan.

Los proyectos quedan vinculados al **nombre del perfil** y guardan una copia en:

```
<proyecto>/nbproject/private/totalnet-profile.properties
```

### Dónde se escribe la configuración

Al aplicar un perfil, el plugin escribe:

- `<proyecto>/nbproject/private/private.properties`
- `<proyecto>/nbproject/private/build.properties`
- `<proyecto>/nbproject/private/tomcat90.properties`
- `<tomcat>/bin/setenv.sh` (Linux) o `<tomcat>/bin/setenv.bat` (Windows)

El `setenv` se actualiza dentro de un bloque gestionado:

```sh
# >>> TOTALNET >>>
export TOTALNET_DB_IP="..."
...
# <<< TOTALNET <<<
```

- Se conserva lo que esté fuera del bloque.
- Se eliminan definiciones duplicadas de variables que maneja el perfil.
- Se hace backup del archivo antes de modificarlo.

### Variables básicas

| Variable | Ejemplo |
|---|---|
| `TOTALNET_DB_IP` | `192.168.1.10:5432/` |
| `TOTALNET_DB_NAME` | `totalnet` |
| `TOTALNET_DB_USER` | `postgres` |
| `TOTALNET_DB_PASSWORD` | `********` |
| `TOTALNET_DB_DRIVER` | `org.postgresql.Driver` |

### Variables opcionales (tabla)

| Variable | Uso |
|---|---|
| `TOTALNET_DB_URL` | Prefijo base para pool/calidad: `jdbc:postgresql://` |
| `TOTALNET_COD_ENTORNO` | Código que se compara con `cpm.entorno` |
| `TOTALNET_ARCHIVOS_PATH` | Ruta base de archivos (NFS) |
| `TOTALNET_DB_IP_CAL` | IP de la BD de calidad (`host:puerto/`) |
| `TOTALNET_DB_NAME_CAL` | Nombre de la BD de calidad |

Puedes agregar cualquier otra variable en la tabla.

---

## Reinicio de Tomcat

Las variables de entorno se leen **al arrancar Tomcat**. Después de aplicar un perfil:

1. Detén Tomcat.
2. Vuelve a arrancarlo.

En NetBeans: `Services`/`Servers` → Tomcat → **Restart**.

Si no lo reinicias, la aplicación seguirá usando las variables anteriores.

---

## Solución de problemas

### No hay perfiles

Es normal en una instalación nueva. Crea uno en **Gestionar perfiles y aplicar**.
Al intentar montar sin perfiles, el plugin ofrece crearlos.

### Tomcat no encontrado

En el perfil, al lado de **Tomcat (home)** hay un icono **ⓘ** con las instrucciones:

1. Descarga Apache Tomcat 9.
2. Descomprímelo en una carpeta (ej. `~/apache-tomcat-9.0.x`).
3. Selecciona esa carpeta en el perfil.

### La aplicación no conecta a la BD

Revisa en el perfil:

- que `TOTALNET_DB_NAME` exista en el servidor,
- que `TOTALNET_DB_IP` termine en `/` (ej. `192.168.30.26:5432/`),
- usuario y contraseña,
- que hayas **reiniciado Tomcat** después de aplicar.

Si el error es `objConexion is null`, la conexión falló; casi siempre es nombre de BD, IP o credenciales.

### Aparece una ventana pidiendo clave SSH

El plugin ejecuta `git`, y SSH pide la passphrase de tu llave. Solución:

```bash
ssh-add ~/.ssh/id_rsa
ssh -T git@github.com   # o gitlab.com
```

Luego reinicia NetBeans si sigue apareciendo.

### El build falla con `copyfiles` o `CopyLibs`

El plugin genera `nbproject/private/build.properties` con las librerías de NetBeans.
Usa la versión más reciente del plugin y vuelve a aplicar el perfil.

### El plugin pide reiniciar / aparece sin firmar

Es normal: el `.nbm` no está firmado. Acepta el aviso.

### El context path de Tomcat no coincide con el nombre del proyecto

El context path viene del archivo versionado `web/META-INF/context.xml` del repositorio.
El plugin no lo modifica. Si necesitas otro, edítalo en el proyecto.

---

## Compilar el plugin desde el código

```bash
cd plugin_deploy_netbeans
./build.sh
```

Genera:

```
build/org-totalnet-bootstrap.nbm
```

`build.sh` usa por defecto `/usr/lib/apache-netbeans` (NetBeans 30) si existe; si no, el snap.
Para indicar otra instalación:

```bash
NB_HOME=/ruta/a/netbeans ./build.sh
```

El `.nbm` resultante funciona en NetBeans 30 y 31.

---

## Estructura del proyecto

```
build.sh                       Compila el plugin y genera el .nbm
manifest.mf                    Manifiesto del módulo NetBeans
nbproject/                     Configuración del proyecto de módulo
src/org/totalnet/bootstrap/
  action/                      Acciones del menú Tools
  model/EnvProfile.java        Modelo del perfil (básicas + extras)
  service/
    AntRunner.java             Ejecuta ant clean dist
    EnvironmentConfigurator.java  Escribe nbproject/private y setenv
    GitService.java            clone/fetch/checkout
    ProfileStore.java          Perfiles en ~/.totalnet/profiles
    ProjectBootstrap.java      Inyecta nbproject/build.xml y limpia boilerplate
  ui/
    MountProjectDialog.java    Wizard de montaje
    ProfileManagerDialog.java  Gestión de perfiles y variables
  resources/                   Plantilla nbproject/build.xml de TotalNet
TotalNetBootstrap.nbm          Plugin listo para instalar
```

---

## Notas

- El plugin **no** registra Tomcat por API: usa el Tomcat que ya tengas registrado en NetBeans
  para ejecutar, y la ruta del perfil para escribir el `setenv`.
- El plugin **no** modifica archivos versionados del repositorio (`src`, `web`, `context.xml`).
- Los perfiles y contraseñas viven fuera del repositorio
  (`~/.totalnet/profiles` y `nbproject/private`).
