// Run from repository root: node scripts/check-light-studio.cjs
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const element = () => ({textContent:'', hidden:true, attributes:{}, handlers:{},
  setAttribute(key,value){this.attributes[key]=value;},
  addEventListener(name,handler){this.handlers[name]=handler;}, replaceChildren(){}});
const nodes = Object.fromEntries(['elevation','scene','fallback','angle','scene-label','tip-title','tip'].map(id=>[id,element()]));
nodes.elevation.value = '25';
const buttons = [0,90,180].map(angle=>Object.assign(element(),{dataset:{angle:String(angle)}}));
// No WebGL/THREE: the accessible lesson must remain usable even when rendering fails.
vm.runInNewContext(fs.readFileSync('app/src/main/assets/light-studio/studio.js','utf8'), {
  window:{}, document:{querySelector:s=>nodes[s.slice(1)],querySelectorAll:()=>buttons}
});
assert.equal(nodes.fallback.hidden,false);
assert.equal(nodes['scene-label'].textContent,'측광 · 25°');
for (const [i,name] of ['순광','측광','역광'].entries()) {
  buttons[i].handlers.click();
  assert.equal(buttons.filter(b=>b.attributes['aria-pressed']==='true').length,1);
  assert.ok(nodes['tip-title'].textContent.startsWith(name));
}
for (const height of [10,75]) {
  nodes.elevation.value=String(height); nodes.elevation.handlers.input();
  assert.equal(nodes.angle.textContent,`${height}°`);
  assert.equal(nodes.elevation.attributes['aria-valuetext'],`${height}도`);
  assert.ok(nodes.scene.attributes['aria-label'].includes(`${height}도`));
}
console.log('PASS: WebGL fallback, three presets, exclusive selection, height endpoints and accessible labels');
