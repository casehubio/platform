import { pathToFileURL } from 'node:url';

const file = process.argv[2];
try {
    const mod = await import(pathToFileURL(file).href);
    const result = mod.default ?? mod;
    process.stdout.write(JSON.stringify(result));
} catch (err) {
    process.stderr.write(JSON.stringify({
        error: err.message,
        stack: err.stack
    }));
    process.exit(1);
}
