import fs from 'node:fs';
import path from 'node:path';

const dir = 'app/src/main/res/drawable';
const files = fs.readdirSync(dir);

for (const file of files) {
    if (file.endsWith('.xml') && file.startsWith('ic_')) {
        const filePath = path.join(dir, file);
        let content = fs.readFileSync(filePath, 'utf8');
        let modified = false;
        if (content.includes('@color/foreground')) {
            content = content.replace(/@color\/foreground(_[a-z]+)?/g, '#000000');
            modified = true;
        }
        if (modified) {
            fs.writeFileSync(filePath, content);
            console.log(`Updated ${file}`);
        }
    }
}
