/* Three.js 0.160.1, local-only educational lighting; no astronomical prediction. */
(() => {
  const slider = document.querySelector('#elevation');
  const buttons = [...document.querySelectorAll('[data-angle]')];
  const host = document.querySelector('#scene');
  const fallback = document.querySelector('#fallback');
  const lessons = {
    0: ['순광', '순광으로 색을 또렷하게 담아요', '카메라 뒤에서 오는 빛은 피사체의 앞면을 고르게 밝혀요. 색과 형태를 담기 좋아요.'],
    90: ['측광', '측광으로 입체감을 살려요', '피사체 옆에서 오는 빛은 밝은 면과 어두운 면을 나눠 질감을 드러내요.'],
    180: ['역광', '역광으로 윤곽을 찾아요', '피사체 뒤에서 오는 빛은 가장자리를 강조해요. 밝은 배경에 노출을 맞추면 실루엣을 담을 수 있어요.']
  };
  let direction = 90;
  let renderer, scene, camera, light, sun;
  function update() {
    const height = Number(slider.value);
    const [name, title, tip] = lessons[direction];
    document.querySelector('#angle').textContent = `${height}°`;
    document.querySelector('#scene-label').textContent = `${name} · ${height}°`;
    document.querySelector('#tip-title').textContent = title;
    document.querySelector('#tip').textContent = tip;
    slider.setAttribute('aria-valuetext', `${height}도`);
    host.setAttribute('aria-label', `${name}, 태양 높이 ${height}도. ${tip}`);
    buttons.forEach(button => button.setAttribute('aria-pressed', String(Number(button.dataset.angle) === direction)));
    if (!renderer) return;
    const azimuth = direction * Math.PI / 180;
    const elevation = height * Math.PI / 180;
    light.position.set(5 * Math.sin(azimuth) * Math.cos(elevation), 5 * Math.sin(elevation), 5 * Math.cos(azimuth) * Math.cos(elevation));
    sun.position.copy(light.position);
    renderer.render(scene, camera);
  }
  buttons.forEach(button => button.addEventListener('click', () => { direction = Number(button.dataset.angle); update(); }));
  slider.addEventListener('input', update);
  try {
    const T = window.THREE;
    scene = new T.Scene();
    scene.background = new T.Color('#efe5d8');
    camera = new T.PerspectiveCamera(45, 1, .1, 50);
    camera.position.set(0, 4.2, 10.8);
    camera.lookAt(0, .7, 0);
    renderer = new T.WebGLRenderer({antialias: true});
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = T.PCFSoftShadowMap;
    host.appendChild(renderer.domElement);
    renderer.domElement.addEventListener('webglcontextlost', event => {
      event.preventDefault(); fallback.hidden = false; renderer.domElement.hidden = true;
    });
    renderer.domElement.addEventListener('webglcontextrestored', () => {
      fallback.hidden = true; renderer.domElement.hidden = false; update();
    });
    scene.add(new T.HemisphereLight(0xfff7e9, 0x554335, .65));
    light = new T.DirectionalLight(0xffdfa2, 3);
    light.castShadow = true;
    light.shadow.mapSize.set(1024, 1024);
    Object.assign(light.shadow.camera, {left:-7,right:7,top:7,bottom:-7,near:.1,far:25});
    light.shadow.bias = -.001;
    scene.add(light);
    const ground = new T.Mesh(new T.CylinderGeometry(4.8, 4.8, .12, 64), new T.MeshStandardMaterial({color:0xdac8af,roughness:1}));
    ground.position.y = -.06; ground.receiveShadow = true; scene.add(ground);
    const body = new T.Mesh(new T.CylinderGeometry(.42, .62, 1.25, 32), new T.MeshStandardMaterial({color:0xb23a1b,roughness:.85}));
    body.position.y = .625; body.castShadow = true; body.receiveShadow = true; scene.add(body);
    const head = new T.Mesh(new T.SphereGeometry(.37, 24, 16), new T.MeshStandardMaterial({color:0xf3ceab,roughness:.9}));
    head.position.y = 1.62; head.castShadow = true; scene.add(head);
    sun = new T.Mesh(new T.SphereGeometry(.16, 16, 12), new T.MeshBasicMaterial({color:0xf7b344}));
    scene.add(sun);
    const resize = new ResizeObserver(() => {
      if (!host.clientWidth) return;
      renderer.setSize(host.clientWidth, host.clientHeight);
      camera.aspect = host.clientWidth / host.clientHeight;
      camera.updateProjectionMatrix(); update();
    });
    resize.observe(host);
    window.addEventListener('pagehide', () => {
      resize.disconnect();
      scene.traverse(object => { object.geometry?.dispose(); object.material?.dispose(); });
      light.shadow.dispose(); renderer.dispose();
    }, {once:true});
  } catch (error) {
    renderer?.dispose(); renderer = null; host.replaceChildren(); fallback.hidden = false;
  }
  update();
})();
