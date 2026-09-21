// Generates the VAPID key pair Web Push needs. Run once, keep the private key secret:
//   node scripts/generate-vapid-keys.mjs
// Then put the three values in the API's environment (server: /opt/basecamp/.env, local: user environment variables).
// Uses only Node's built-in crypto module (Node 16+).
import { generateKeyPairSync } from 'node:crypto';

const { privateKey } = generateKeyPairSync('ec', { namedCurve: 'P-256' });
const jwk = privateKey.export({ format: 'jwk' });

// public key = uncompressed EC point (0x04 | x | y), base64url; private key = the scalar d, base64url
const publicKey = Buffer.concat([
	Buffer.from([0x04]),
	Buffer.from(jwk.x, 'base64url'),
	Buffer.from(jwk.y, 'base64url')
]).toString('base64url');

console.log('Add these to the API environment (never commit them):\n');
console.log(`VAPID_PUBLIC_KEY=${publicKey}`);
console.log(`VAPID_PRIVATE_KEY=${jwk.d}`);
console.log('VAPID_SUBJECT=mailto:you@example.com   # your own address; push services use it to contact you');
