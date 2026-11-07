// Local AsciiDoc preview and source-reference checks.
// SPDX-License-Identifier: Apache-2.0
const fs = require('node:fs');
const path = require('node:path');
const asciidoctor = require('@asciidoctor/core');

async function main() {
  const docRoot = path.resolve(__dirname, '..');
  const root = path.resolve(docRoot, '../..');
  const src = path.join(docRoot, 'src/asciibinder');
  const out = path.join(docRoot, 'build/preview');
  const docs = [];
  const ids = new Map();
  const refs = [];
  let failures = 0;

  function report(message, file, target) {
    console.error(message, path.relative(src, file), target);
    failures++;
  }

  function walk(dir) {
    for (const entry of fs.readdirSync(dir, {withFileTypes: true})) {
      if (entry.name.startsWith('_')) continue;
      const file = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(file);
      else if (entry.name.endsWith('.adoc')) docs.push(file);
    }
  }

  function copySchemas(dir) {
    for (const entry of fs.readdirSync(dir, {withFileTypes: true})) {
      if (['build', '.gradle', 'node_modules'].includes(entry.name)) continue;
      const file = path.join(dir, entry.name);
      if (entry.isDirectory()) copySchemas(file);
      else if (entry.name.endsWith('.xsd') && file.includes('/src/main/resources/META-INF/schema/')) {
        fs.copyFileSync(file, path.join(out, 'schemas', entry.name));
      }
    }
  }

  walk(src);
  fs.mkdirSync(path.join(out, 'schemas'), {recursive: true});
  fs.cpSync(path.join(src, '_images'), path.join(out, '_images'), {recursive: true});
  copySchemas(path.join(root, 'repose-aggregator/core'));
  copySchemas(path.join(root, 'repose-aggregator/components'));
  const logger = asciidoctor.MemoryLogger.create();
  asciidoctor.LoggerManager.setLogger(logger);

  for (const file of docs) {
    const text = fs.readFileSync(file, 'utf8');
    let badInclude = false;
    for (const match of text.matchAll(/include::([^\[\n]+)\[/g)) {
      const target = path.resolve(path.dirname(file), match[1]);
      if (!target.startsWith(root + path.sep) || !fs.existsSync(target)) {
        report('BAD INCLUDE', file, match[1]);
        badInclude = true;
      }
    }
    if (badInclude) continue;
    for (const match of text.matchAll(/<<([^>,\n]+\.adoc)(?:#([^>,]*))?(?:,[^>]*)?>>/g)) {
      refs.push([file, match[1], match[2]]);
    }
    for (const match of text.matchAll(/image::([^\[\n]+)\[/g)) {
      if (/^(https?:|\{)/.test(match[1])) continue;
      if (!fs.existsSync(path.resolve(path.dirname(file), match[1]))) {
        report('BAD IMAGE', file, match[1]);
      }
    }
    for (const match of text.matchAll(/link:([^\[\n]+)\[/g)) {
      if (/^(https?:|\{)/.test(match[1])) continue;
      const target = path.resolve(path.dirname(file), match[1]);
      const generated = path.resolve(out, path.relative(src, target));
      if (!fs.existsSync(target) && !fs.existsSync(generated)) {
        report('BAD LINK', file, match[1]);
      }
    }
    const target = path.join(out, path.relative(src, file).replace(/\.adoc$/, '.html'));
    fs.mkdirSync(path.dirname(target), {recursive: true});
    const version = process.env.REPOSE_DOC_VERSION || 'development';
    const options = {
      safe: 'unsafe',
      standalone: true,
      base_dir: path.dirname(file),
      to_file: target,
      attributes: {'project-version': version, 'repose-version': version}
    };
    const ast = await asciidoctor.loadFile(file, options);
    ids.set(file, new Set(ast.findBy({}).map(node => node.getId?.()).filter(Boolean)));
    await asciidoctor.convertFile(file, options);
  }

  for (const [file, target, anchor] of refs) {
    const destination = path.resolve(path.dirname(file), target);
    if (!fs.existsSync(destination)) report('BAD XREF FILE', file, target);
    else if (anchor && ids.has(destination) && !ids.get(destination).has(anchor)) {
      report('BAD XREF ID', file, target + '#' + anchor);
    }
  }
  const messages = logger.getMessages();
  for (const message of messages) {
    console.error('RENDER', message.getSeverity?.() ?? message.severity,
      String(message.getMessage?.() ?? JSON.stringify(message.message)));
  }
  fs.writeFileSync(path.join(out, 'index.html'),
    '<!doctype html><meta http-equiv="refresh" content="0;url=welcome/index.html">');
  console.log(JSON.stringify({
    pages: docs.length, localCrossReferences: refs.length, failures, rendererMessages: messages.length
  }));
  process.exitCode = failures || messages.length ? 1 : 0;
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
