const data = JSON.parse(require('fs').readFileSync('/dev/stdin', 'utf8'));
console.log(JSON.stringify({result: 'transformed'}));
