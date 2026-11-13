# talki-session-services

Servicios del bounded context **Practice Session** de Talki:

| Servicio | Puerto | Descripción |
|----------|--------|-------------|
| `session-service` | 8082 | CRUD de sesiones de práctica, feedback, proyección de usuarios (CQRS + ACL) |
| `live-coach-service` | 8083 | Proxy Gemini Live (tokens efímeros) + Strategy por modo de sesión |

Java 17 · Spring Boot 3.2.x · Maven · H2 (memoria) · RabbitMQ (`talki.events`).

## Variables de entorno

| Variable | Servicios | Obligatoria | Descripción |
|----------|-----------|-------------|-------------|
| `JWT_SECRET` | ambos | ✅ | Secreto HS256 compartido con identity-service. **Sin valor por defecto**: si falta, el servicio falla al arrancar. |
| `RABBITMQ_HOST/PORT/USERNAME/PASSWORD/VHOST` | ambos | no | Broker de eventos (default localhost/guest). |
| `GEMINI_API_KEY` | live-coach | no | Sin ella `/v1/coach/live-token` responde 503; el resto funciona. |
| `GEMINI_MODEL` | live-coach | no | Modelo Live (default `gemini-2.5-flash-native-audio-latest`). |

## Autenticación (SB1-02-B)

Todos los endpoints `/v1/**` exigen `Authorization: Bearer <JWT>` firmado con
`JWT_SECRET` (HS256, librería jjwt, igual que identity-service). El token debe
traer el claim `userId`. Sin token, inválido o vencido → **401**. Acceder a una
sesión ajena → **403**. El `userId` del body/query **se ignora**: la identidad
del token prevalece.

Exclusiones públicas: `/swagger-ui`, `/v3/api-docs`, `/actuator`.

### Tokens de prueba para A y B

**Opción real (recomendada):** levanta identity-service y registra/loguea dos
usuarios; cada login devuelve el access token de 15 min:

```bash
curl -X POST http://localhost:8081/auth/login -H "Content-Type: application/json" \
  -d '{"email":"a@talki.pe","password":"..."}'   # → accessToken de A
curl -X POST http://localhost:8081/auth/login -H "Content-Type: application/json" \
  -d '{"email":"b@talki.pe","password":"..."}'   # → accessToken de B
```

**Opción rápida (sin identity-service):** mintea tokens con el mismo secreto.
Crea un archivo `TokenMint.java` (o usa jshell) con jjwt en el classpath:

```java
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;

var key = Keys.hmacShaKeyFor(System.getenv("JWT_SECRET").getBytes(StandardCharsets.UTF_8));
String userA = Jwts.builder().setSubject("a@talki.pe").claim("userId", "1")
        .setExpiration(new Date(System.currentTimeMillis() + 900_000))
        .signWith(key, SignatureAlgorithm.HS256).compact();
String userB = Jwts.builder().setSubject("b@talki.pe").claim("userId", "2")
        .setExpiration(new Date(System.currentTimeMillis() + 900_000))
        .signWith(key, SignatureAlgorithm.HS256).compact();
System.out.println("A: " + userA + "\nB: " + userB);
```

Uso:

```bash
export JWT_SECRET='<mismo-secreto-de-identity-min-32-bytes>'
docker compose -f docker-compose.dev.yml up -d

curl -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" \
  -d '{"title":"Pitch de A","sessionType":"PITCH"}' http://localhost:8082/v1/sessions
curl -H "Authorization: Bearer $TOKEN_B" http://localhost:8082/v1/sessions/1   # → 403 si es de A
```

> Los tokens de prueba valen solo si el userId existe en la proyección `users`
> (evento `user.registered` consumido desde identity). Para probar 403/404 no
> hace falta que exista: el control de propiedad va antes.

## Cómo correr tests

```bash
cd session-service && mvn -B -ntp clean test
cd live-coach-service && mvn -B -ntp clean test
```

## Privacidad

Ver [`docs/privacy-gate.md`](docs/privacy-gate.md) (SB1-07-D).
