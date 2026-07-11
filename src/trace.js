export function printTaskGraph(state) {
  console.log('Task graph render not yet implemented.');
}

export function traceSense(msg) {
  console.log(`\x1b[36m[Sense]\x1b[0m ${msg}`);
}

export function traceDecide(model, msg) {
  console.log(`\x1b[33m[Decide] [${model}]\x1b[0m ${msg}`);
}

export function traceAct(msg) {
  console.log(`\x1b[32m[Act]\x1b[0m ${msg}`);
}

export function traceCheck(msg) {
  console.log(`\x1b[35m[Check]\x1b[0m ${msg}`);
}

export function traceError(msg) {
  console.log(`\x1b[31m[Error]\x1b[0m ${msg}`);
}

export function traceEscalation(msg) {
  console.log(`\x1b[1m\x1b[37m[Escalation]\x1b[0m ${msg}`);
}
