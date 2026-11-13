# Privacy Gate — SB1-07-D

Estado de privacidad y protección de datos del dominio **Practice Session**
(session-service 8082, live-coach-service 8083) al cierre de SB1-02-B.

## Bloqueos y riesgos activos

| # | Tema | Estado | Detalle |
|---|------|--------|---------|
| 1 | Endpoints internos de bloqueo | ⛔ No comprobable | `/internal/v1/sessions/{id}/access` y `/access-blocks` **no existen**. El bloqueo de nuevo procesamiento (US27 / derecho de oposición) no puede verificarse end-to-end. Bloqueado por SB1-03 (Alejandro). |
| 2 | Datos personales en fixtures | ⚠️ Limitado | Ambiente restringido a fixtures H2 en memoria (`ddl-auto: create-drop`, `jdbc:h2:mem`). **No cargar datos personales reales** hasta G-PRIV (US27, US33, TS06). PostgreSQL solo en prod (`application-prod.yml`). |
| 3 | `transcriptGemini` en query params | ⚠️ Riesgo | `POST /v1/coach/{sessionId}/finalize` recibe el transcript en query params → puede quedar en logs de acceso, proxies y historial. **Propuesta (pendiente de coordinar con Kevin): moverlo al body** de la request. NO se movió en SB1-02-B por alcance. |
| 4 | Audio crudo | ✅ OK | No hay persistencia de audio crudo: el navegador habla directo con Gemini Live vía token efímero; solo métricas/transcript pasan por live-coach. |
| 5 | `GEMINI_API_KEY` | ✅ OK | Solo en el servidor (inyección por entorno, nunca commiteada). El navegador recibe un token efímero de un solo uso con TTL corto. |
| 6 | Logout vs access token | ⚠️ Aceptado | El logout de identity-service no se propaga a session/live-coach: un access token emitido sigue válido **hasta 15 min** (TTL actual). Ventana conocida y acotada. |
| 7 | Swagger | ⚠️ Público | `/swagger-ui` y `/v3/api-docs` están excluidos del filtro JWT para poder documentar/integrar. Antes de prod, decidir si se protegen o se apagan. |
| 8 | `show-sql` y `logging DEBUG` | ✅ OK | Solo en perfil `dev` (`application-dev.yml`); en default/prod quedan apagados para no volcar datos a logs. |

## Mitigaciones ya implementadas en SB1-02-B

- Identidad por JWT validado localmente (HS256, `JWT_SECRET` compartido con identity-service): el userId del **token prevalece** sobre cualquier userId del cliente.
- 403/404 sin filtración: los cuerpos de error **no exponen** título, estado ni feedback de la sesión.
- Auditoría QAS-SEC-01: cada 401/403 genera log estructurado y evento `security.access.denied` **sin token ni datos de sesión**.
