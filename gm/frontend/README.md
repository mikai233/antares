# GM Console Frontend

Vue 3 + Vite + TypeScript + Element Plus frontend for the GM Spring Boot service.

## Commands

```bash
npm install
npm run dev
npm run build
```

The Vite dev server proxies `/gm/api`, `/script`, and `/actuator` to `http://127.0.0.1:18080`.

## Structure

- `src/api`: REST client wrappers.
- `src/components`: shared layout components.
- `src/router`: page routes.
- `src/stores`: Pinia stores.
- `src/views`: route views.
