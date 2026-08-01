#!/usr/bin/env node
/**
 * Driver Location Simulator
 *
 * Simulates a driver moving along a route on Galle Road (A2 Highway), Colombo to Galle,
 * sending GPS updates via STOMP WebSocket. This simulates exactly how the mobile app
 * sends location data to the backend.
 *
 * =============================================================================
 * SETUP
 * =============================================================================
 *
 * Prerequisites:
 *   1. Backend must be running on http://localhost:8085 (or specify --api-url)
 *   2. Dependencies must be installed:
 *      npm install sockjs-client stompjs
 *
 * Quick Start:
 *   node scripts/driver-location-simulator.js
 *
 * =============================================================================
 * USAGE
 * =============================================================================
 *
 *   node scripts/driver-location-simulator.js [options]
 *
 * Options:
 *   --driver-email     Driver email (default: driver-sim@ebee.lk)
 *   --driver-password  Driver password (default: Driver@123)
 *   --speed            Movement speed in km/h (default: 40)
 *   --interval        GPS update interval in ms (default: 3000ms)
 *   --api-url          Backend API URL (default: http://localhost:8085)
 *
 * Examples:
 *   # Run at 60km/h
 *   node scripts/driver-location-simulator.js --speed 60
 *
 *   # Run at 500ms intervals (2 updates per second)
 *   node scripts/driver-location-simulator.js --interval 500
 *
 *   # Point to production backend
 *   node scripts/driver-location-simulator.js --api-url https://api.ebee.lk
 *
 * =============================================================================
 * ROUTE
 * =============================================================================
 *
 * The simulator traces Galle Road (A2 Highway) from Fort Colombo to Galle and back:
 *   - Fort, Colombo (6.9271, 79.8412) → Galle (6.0255, 80.2225)
 *   - Distance: ~120km one way
 *   - At 40km/h: ~3 hours for full round trip
 *
 * The route follows actual waypoints along A2 highway passing through:
 *   Mount Lavinia, Kalutara, Beruwala, Aluthgama, Bentota, Hikkaduwa,
 *   Ambalangoda, Galle, and returning northbound.
 *
 * =============================================================================
 * HOW IT WORKS
 * =============================================================================
 *
 * 1. Creates/retrieves a test driver account via admin API
 * 2. Authenticates as the driver
 * 3. Connects to STOMP WebSocket endpoint at /ws
 * 4. Continuously:
 *    - Calculates current position along route based on elapsed time
 *    - Sends location update every --interval ms
 *    - Interpolates between waypoints for smooth movement
 *
 * Data sent per update:
 *   {
 *     latitude: number,
 *     longitude: number,
 *     accuracy: number (5.0m),
 *     bearing: number (degrees),
 *     speed: number (km/h)
 *   }
 *
 * =============================================================================
 * BACKEND INTEGRATION
 * =============================================================================
 *
 * The simulator exercises the same endpoints as the mobile app:
 *   - POST /api/drivers (create driver - admin required)
 *   - POST /api/auth/login (authenticate)
 *   - PATCH /api/drivers/{id}/availability (set available)
 *   - STOMP /app/driver-locations/update (send GPS)
 *
 * Backend receives location and broadcasts to /topic/driver-locations
 * Frontend subscribes to same topic for live updates.
 *
 * =============================================================================
 */

const SockJS = require('sockjs-client');
const Stomp = require('stompjs');
const http = require('http');

// Parse command line args
const args = process.argv.slice(2);
const getArg = (name, defaultVal) => {
  const idx = args.indexOf(`--${name}`);
  return idx !== -1 ? args[idx + 1] : defaultVal;
};

const DRIVER_EMAIL = getArg('driver-email', 'driver-sim@ebee.lk');
const DRIVER_PASSWORD = getArg('driver-password', 'Driver@123');
const ADMIN_EMAIL = getArg('admin-email', 'super@ebee.lk');
const ADMIN_PASSWORD = getArg('admin-password', 'Super@dmin123!');
const SPEED_KMH = parseFloat(getArg('speed', '40'));
const INTERVAL_MS = parseInt(getArg('interval', '3000'));
const API_URL = getArg('api-url', 'http://localhost:8085');

// Sri Lanka route - Galle Road (A2 Highway), Colombo to Galle and back
const ROUTE = [
  // Colombo to Galle (southbound)
  { lat: 6.9271, lng: 79.8412 },  // Fort, Colombo
  { lat: 6.9219, lng: 79.8425 },  // Kollupitiya
  { lat: 6.9173, lng: 79.8438 },  // Bambalapitiya
  { lat: 6.9127, lng: 79.8451 },  // Wellawatte
  { lat: 6.9081, lng: 79.8464 },  // Wellawatte North
  { lat: 6.9035, lng: 79.8477 },  // Dehiwala
  { lat: 6.8989, lng: 79.8490 },  // Oluwil
  { lat: 6.8943, lng: 79.8503 },  // Mount Lavinia
  { lat: 6.8897, lng: 79.8516 },  // Ratmalana
  { lat: 6.8851, lng: 79.8529 },  // Kalubawabede
  { lat: 6.8805, lng: 79.8542 },  // Panadura
  { lat: 6.8759, lng: 79.8555 },  // Walana
  { lat: 6.8713, lng: 79.8568 },  // Neboda
  { lat: 6.8667, lng: 79.8581 },  // Kalutara
  { lat: 6.8621, lng: 79.8594 },  // Payable
  { lat: 6.8575, lng: 79.8607 },  // Beruwala
  { lat: 6.8529, lng: 79.8620 },  // Aluthgama
  { lat: 6.8483, lng: 79.8633 },  // Bentota
  { lat: 6.8437, lng: 79.8646 },  // Induruwa
  { lat: 6.8391, lng: 79.8659 },  // Kosgoda
  { lat: 6.8345, lng: 79.8672 },  // Ahungalla
  { lat: 6.8299, lng: 79.8685 },  // Balapitiya
  { lat: 6.8253, lng: 79.8698 },  // Ambalangoda
  { lat: 6.8207, lng: 79.8711 },  // Hikkaduwa
  { lat: 6.8161, lng: 79.8724 },  // Telwatta
  { lat: 6.8115, lng: 79.8737 },  // Boossa
  { lat: 6.8069, lng: 79.8750 },  // Koggalla
  { lat: 6.8023, lng: 79.8763 },  // Ahangama
  { lat: 6.7977, lng: 79.8776 },  // Weligama
  { lat: 6.7931, lng: 79.8789 },  // Polhena
  { lat: 6.7885, lng: 79.8802 },  // Matara
  { lat: 6.7839, lng: 79.8815 },  // Mirissa
  { lat: 6.7793, lng: 79.8828 },  // Dondra
  { lat: 6.7747, lng: 79.8841 },  // Mulkern
  { lat: 6.7701, lng: 79.8854 },  // Tangalle
  { lat: 6.7655, lng: 79.8867 },  // Seenimodara
  { lat: 6.7609, lng: 79.8880 },  // Hambantota
  { lat: 6.7563, lng: 79.8893 },  // Tissamaharama
  { lat: 6.7517, lng: 79.8906 },  // Yala
  { lat: 6.7471, lng: 79.8919 },  // Kataragama
  { lat: 6.7499, lng: 79.8624 },  // Galle (city center)
  // Galle to Colombo (northbound - reverse)
  { lat: 6.7563, lng: 79.8893 },  // Tissamaharama
  { lat: 6.7609, lng: 79.8880 },  // Hambantota
  { lat: 6.7655, lng: 79.8867 },  // Seenimodara
  { lat: 6.7701, lng: 79.8854 },  // Tangalle
  { lat: 6.7747, lng: 79.8841 },  // Mulkern
  { lat: 6.7793, lng: 79.8828 },  // Dondra
  { lat: 6.7839, lng: 79.8815 },  // Mirissa
  { lat: 6.7885, lng: 79.8802 },  // Matara
  { lat: 6.7931, lng: 79.8789 },  // Polhena
  { lat: 6.7977, lng: 79.8776 },  // Weligama
  { lat: 6.8023, lng: 79.8763 },  // Ahangama
  { lat: 6.8069, lng: 79.8750 },  // Koggalla
  { lat: 6.8115, lng: 79.8737 },  // Boossa
  { lat: 6.8161, lng: 79.8724 },  // Telwatta
  { lat: 6.8207, lng: 79.8711 },  // Hikkaduwa
  { lat: 6.8253, lng: 79.8698 },  // Ambalangoda
  { lat: 6.8299, lng: 79.8685 },  // Balapitiya
  { lat: 6.8345, lng: 79.8672 },  // Kosgoda
  { lat: 6.8391, lng: 79.8659 },  // Induruwa
  { lat: 6.8437, lng: 79.8646 },  // Bentota
  { lat: 6.8483, lng: 79.8633 },  // Aluthgama
  { lat: 6.8529, lng: 79.8620 },  // Beruwala
  { lat: 6.8575, lng: 79.8607 },  // Payable
  { lat: 6.8621, lng: 79.8594 },  // Kalutara
  { lat: 6.8667, lng: 79.8581 },  // Neboda
  { lat: 6.8713, lng: 79.8568 },  // Walana
  { lat: 6.8759, lng: 79.8555 },  // Panadura
  { lat: 6.8805, lng: 79.8542 },  // Kalubawabede
  { lat: 6.8851, lng: 79.8529 },  // Ratmalana
  { lat: 6.8897, lng: 79.8516 },  // Mount Lavinia
  { lat: 6.8943, lng: 79.8503 },  // Oluwil
  { lat: 6.8989, lng: 79.8490 },  // Dehiwala
  { lat: 6.9035, lng: 79.8477 },  // Wellawatte South
  { lat: 6.9081, lng: 79.8464 },  // Wellawatte North
  { lat: 6.9127, lng: 79.8451 },  // Bambalapitiya
  { lat: 6.9173, lng: 79.8438 },  // Kollupitiya
  { lat: 6.9219, lng: 79.8425 },  // Fort
];

let currentWaypointIndex = 0;
let seq = 0;
let driverId = null;
let driverName = null;
let jwtToken = null;
let driverUUID = null;

// Calculate bearing between two points
function calculateBearing(lat1, lng1, lat2, lng2) {
  const dLng = (lng2 - lng1) * Math.PI / 180;
  const lat1Rad = lat1 * Math.PI / 180;
  const lat2Rad = lat2 * Math.PI / 180;

  const y = Math.sin(dLng) * Math.cos(lat2Rad);
  const x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
            Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLng);

  let bearing = Math.atan2(y, x) * 180 / Math.PI;
  return (bearing + 360) % 360;
}

// Interpolate between two points based on progress (0-1)
function interpolatePoint(p1, p2, progress) {
  return {
    lat: p1.lat + (p2.lat - p1.lat) * progress,
    lng: p1.lng + (p2.lng - p1.lng) * progress
  };
}

// Get current position along the route
function getCurrentPosition() {
  const from = ROUTE[currentWaypointIndex];
  const to = ROUTE[(currentWaypointIndex + 1) % ROUTE.length];

  // Calculate distance between waypoints (simplified)
  const distLat = Math.abs(to.lat - from.lat);
  const distLng = Math.abs(to.lng - from.lng);
  const totalDist = Math.sqrt(distLat * distLat + distLng * distLng);

  // Time to travel between waypoints at given speed
  // Speed is in km/h, distance is in degrees (approximate)
  // 1 degree lat ≈ 111km
  const distanceKm = totalDist * 111;
  const timeSeconds = (distanceKm / SPEED_KMH) * 3600;

  // How far along are we in this segment?
  const progress = (Date.now() % (timeSeconds * 1000)) / (timeSeconds * 1000);

  const position = interpolatePoint(from, to, Math.min(progress, 1));
  const bearing = calculateBearing(from.lat, from.lng, to.lat, to.lng);

  return {
    lat: position.lat,
    lng: position.lng,
    bearing,
    speed: SPEED_KMH,
    accuracy: 5.0
  };
}

// Move to next waypoint
function advanceWaypoint() {
  currentWaypointIndex = (currentWaypointIndex + 1) % ROUTE.length;
  if (currentWaypointIndex % 3 === 0) {
    console.log(`[SIM] Advanced to waypoint ${currentWaypointIndex}: lat=${ROUTE[currentWaypointIndex].lat.toFixed(6)}, lng=${ROUTE[currentWaypointIndex].lng.toFixed(6)}`);
  }
}

// HTTP request helper
function httpRequest(method, path, body, token) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, API_URL);
    const options = {
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(token && { 'Authorization': `Bearer ${token}` })
      }
    };

    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, data: JSON.parse(data) });
        } catch {
          resolve({ status: res.statusCode, data });
        }
      });
    });

    req.on('error', reject);
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}

// Extract data from wrapped response
function unwrapResponse(response) {
  if (response.data && response.data.data !== undefined) {
    return response.data.data;
  }
  return response.data;
}

// Login as driver
async function login(email, password) {
  console.log(`[AUTH] Logging in as ${email}...`);
  const response = await httpRequest('POST', '/api/auth/login', { email, password, role: 'DRIVER' });

  const data = unwrapResponse(response);
  if (response.status !== 200 || !data.token) {
    throw new Error(`Login failed: ${JSON.stringify(response.data)}`);
  }

  console.log(`[AUTH] Login successful, token: ${data.token.substring(0, 20)}...`);
  return data;
}

// Create a new driver (requires admin)
async function createDriver() {
  console.log('[ADMIN] Creating new driver...');

  // First login as admin
  const adminLogin = await httpRequest('POST', '/api/auth/login', {
    email: ADMIN_EMAIL,
    password: ADMIN_PASSWORD
  });

  const adminData = unwrapResponse(adminLogin);
  if (adminLogin.status !== 200 || !adminData.token) {
    throw new Error(`Admin login failed: ${JSON.stringify(adminLogin.data)}`);
  }

  const adminToken = adminData.token;

  // Create driver
  const timestamp = Date.now();
  const driverData = {
    email: DRIVER_EMAIL,
    password: DRIVER_PASSWORD,
    firstName: 'Simulation',
    lastName: 'Driver',
    phone: '+94771234567',
    licenseNumber: `LIC${timestamp}`,
    availability: 'AVAILABLE'
  };

  const createResponse = await httpRequest('POST', '/api/drivers', driverData, adminToken);

  if (createResponse.status === 201) {
    console.log(`[ADMIN] Driver created: ${DRIVER_EMAIL}`);
    const data = unwrapResponse(createResponse);
    driverUUID = data.id;
    return data;
  } else if (createResponse.data && createResponse.data.message && createResponse.data.message.includes('already exists')) {
    console.log(`[ADMIN] Driver already exists, using existing driver`);
    // Find the existing driver UUID
    const driversResp = await httpRequest('GET', '/api/drivers?page=0&size=100', null, adminToken);
    const driversData = unwrapResponse(driversResp);
    const driverList = driversData.content || driversData.data?.content || [];
    const existingDriver = driverList.find(d => d.email === DRIVER_EMAIL);
    if (existingDriver) {
      driverUUID = existingDriver.id;
      console.log(`[ADMIN] Found driver UUID: ${driverUUID}`);
    }
    return { email: DRIVER_EMAIL };
  } else {
    throw new Error(`Driver creation failed: ${JSON.stringify(createResponse.data)}`);
  }
}

// Set driver availability
async function setAvailability(token, availability) {
  if (!driverUUID) {
    console.log('[WARN] No driver UUID, skipping availability update');
    return;
  }
  const response = await httpRequest('PATCH', `/api/drivers/${driverUUID}/availability`, { availability }, token);
  if (response.status !== 200) {
    console.warn(`[WARN] Failed to set availability: ${JSON.stringify(response.data)}`);
  }
}

// Connect to STOMP WebSocket and send location updates
function connectAndSendLocations() {
  return new Promise((resolve, reject) => {
    const socketUrl = API_URL + '/ws';
    console.log(`[WS] Connecting to ${socketUrl}...`);

    const socket = new SockJS(socketUrl);
    const client = Stomp.over(socket);

    // Disable heartbeat for simplicity
    client.heartbeat.outgoing = 0;
    client.heartbeat.incoming = 0;

    client.connect(
      { Authorization: `Bearer ${jwtToken}` },
      () => {
        console.log('[STOMP] Connected to STOMP broker');

        // Subscribe to confirmation channel
        client.subscribe('/user/queue/location-confirm', (message) => {
          console.log(`[STOMP] Location confirmed: ${message.body}`);
        });

        // Start sending location updates
        startLocationUpdates(client);
        resolve();
      },
      (error) => {
        console.error('[STOMP] Connection error:', error);
        reject(error);
      }
    );

    // Handle errors
    socket.onerror = (error) => {
      console.error('[WS] Socket error:', error);
    };

    socket.onclose = () => {
      console.log('[WS] Connection closed');
    };

    // Handle SIGINT to clean disconnect
    process.on('SIGINT', () => {
      console.log('\n[SIM] Shutting down...');
      if (client.connected) {
        client.disconnect();
      }
      process.exit(0);
    });
  });
}

// Send location update via STOMP
function sendLocationUpdate(client, position) {
  if (!client.connected) {
    console.warn('[STOMP] Cannot send, not connected');
    return;
  }

  seq++;
  const locationData = {
    latitude: position.lat,
    longitude: position.lng,
    accuracy: position.accuracy,
    bearing: position.bearing,
    speed: position.speed
  };

  client.send('/app/driver-locations/update', {
    'content-type': 'application/json',
    Authorization: `Bearer ${jwtToken}`
  }, JSON.stringify(locationData));

  // Log every 10th update
  if (seq % 10 === 0 || seq <= 3) {
    console.log(`[GPS] #${seq} lat=${position.lat.toFixed(6)} lng=${position.lng.toFixed(6)} bearing=${position.bearing.toFixed(0)}° speed=${position.speed}km/h`);
  }
}

// Start the location update loop
let updateInterval;
function startLocationUpdates(client) {
  console.log(`[SIM] Starting location updates every ${INTERVAL_MS}ms at ${SPEED_KMH}km/h`);
  console.log('[SIM] Open the tracking page in the frontend to see the truck move!');

  // Send first update immediately
  sendLocationUpdate(client, getCurrentPosition());

  updateInterval = setInterval(() => {
    const position = getCurrentPosition();
    sendLocationUpdate(client, position);
    advanceWaypoint();
  }, INTERVAL_MS);
}

// Main execution
async function main() {
  try {
    console.log('===========================================');
    console.log('  Driver Location Simulator');
    console.log('===========================================');
    console.log(`  Driver:    ${DRIVER_EMAIL}`);
    console.log(`  Speed:     ${SPEED_KMH} km/h`);
    console.log(`  Interval:  ${INTERVAL_MS} ms`);
    console.log(`  API URL:   ${API_URL}`);
    console.log('===========================================\n');

    // Step 1: Create or get existing driver
    await createDriver();

    // Step 2: Login as the driver
    const loginData = await login(DRIVER_EMAIL, DRIVER_PASSWORD);
    jwtToken = loginData.token;
    driverId = loginData.driverId;
    driverName = `${loginData.firstName} ${loginData.lastName}`;
    console.log(`[AUTH] Logged in as driver: ${driverName} (${driverId || 'no driverId'})`);

    // Step 3: Set driver availability to AVAILABLE
    await setAvailability(jwtToken, 'AVAILABLE');
    console.log('[STATUS] Driver availability set to AVAILABLE');

    // Step 4: Connect to WebSocket and start sending locations
    await connectAndSendLocations();

    // Keep running
    console.log('\n[SIM] Simulator running. Press Ctrl+C to stop.\n');

    // Keep the process alive
    process.stdin.resume();

  } catch (error) {
    console.error('[ERROR]', error.message);
    process.exit(1);
  }
}

main();
