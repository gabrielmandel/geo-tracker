# GpsTracker

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 22.2.0.

# GEO TRACKER

An Angular 22 fleet operations mockup with a Leaflet geofence map, polygon drawing, and a Material-styled sign-in screen.

## Run locally

```bash
npm install
npm start
```

Open `http://localhost:4200/`. The root route redirects to `/login`; the Connect button opens `/home` without authenticating against a server.

## Geofences

On `/home`, use **Load geofences** to display the seeded Seattle boundaries. Use **Draw polygon** to create a boundary on the map, name it, and save it. Reads and saves are held in the in-memory `GeofenceService`; refreshing the page restores the sample data. The basemap is served by OpenStreetMap.

## Checks

```bash
npm run build
npm test
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
