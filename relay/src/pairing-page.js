/** Callers escape dynamic values before interpolating them into content. */
export function page(title, content) {
  const safeTitle = String(title).replace(/[&<>"']/g, char => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[char]);
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="referrer" content="no-referrer"><meta name="robots" content="noindex,nofollow">
<title>${safeTitle} · AetherScript</title>
<style>
:root{color-scheme:light dark;font-family:system-ui,sans-serif;color:#152944;background:#edf5ff}
*{box-sizing:border-box}body{margin:0;min-height:100vh;background:linear-gradient(150deg,#daedff,#f6fbff 62%);line-height:1.55}
main{margin:auto;max-width:40rem;padding:clamp(1.2rem,5vw,3rem) 1rem}
header{display:flex;align-items:center;gap:.6rem;color:#254c77;font-weight:720;margin-bottom:1.4rem}
header svg{width:2.8rem;height:2.3rem}
article{background:white;border:1px solid #cbdcec;border-radius:1.25rem;padding:clamp(1.25rem,4vw,2.25rem);box-shadow:0 1rem 3rem #4679aa16}
h1{margin:0 0 .7rem;font-size:clamp(1.6rem,5vw,2.2rem);line-height:1.18}h2{font-size:1.2rem}
label,legend{font-weight:620}label{display:block;margin-top:1rem}
input:not([type=hidden]),select,textarea{display:block;width:100%;margin-top:.35rem;border:1px solid #7992a9;border-radius:.5rem;background:#fff;color:#152944;padding:.65rem;font:inherit}
input[type=checkbox],input[type=radio]{display:inline;width:auto}
button{border:0;border-radius:.55rem;background:#1554a3;color:#fff;font:inherit;font-weight:650;padding:.7rem 1.15rem;margin-top:1rem;cursor:pointer}
button:hover{background:#123f77}:focus-visible{outline:3px solid #dc830e;outline-offset:3px}
a{color:#1355a6}code,pre{overflow-wrap:anywhere;white-space:pre-wrap}fieldset{border:0;padding:0;margin:1rem 0}
@media(prefers-color-scheme:dark){:root,body{background:#101e30;color:#e8f2ff}article{background:#192a3f;border-color:#344d69}header,a{color:#9dcdff}input:not([type=hidden]),textarea,select{background:#102036;color:#e8f2ff}button{background:#316eb9}}
</style></head><body><main><header><svg aria-hidden="true" viewBox="0 0 48 36"><path fill="#b7dcff" stroke="#426f9e" stroke-width="2" d="M12 31a9 9 0 0 1-1-18 13 13 0 0 1 25-1 10 10 0 0 1 0 19Z"/><g fill="#244a70"><circle cx="20" cy="20" r="1.4"/><circle cx="29" cy="20" r="1.4"/></g><path d="M22 23q2.5 3 5 0" fill="none" stroke="#244a70" stroke-width="1.4" stroke-linecap="round"/></svg>AetherScript</header><article>${content}</article></main></body></html>`;
}
