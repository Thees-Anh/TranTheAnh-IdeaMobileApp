"""Species attachment profiles in original SVG coordinates; never screen pixels."""
import xml.etree.ElementTree as E
NS='http://www.w3.org/2000/svg'
E.register_namespace('',NS)
PROFILES={
 'puppy':('dog',202,105,180,147,235,145,172,199,242),
 'capybara':('capy',197,94,184,134,218,133,178,190,238),
 'rabbit':('bunny',198,104,177,155,235,153,165,196,241),
}
def attach(svg,key):
    root=E.fromstring(svg)
    prefix,hx,hy,lx,ly,rx,ry,nx,ny,nright=PROFILES[key]
    head=root.find(f".//*[@id='{prefix}-head-group']")
    assert head is not None,key
    equipment=f'''<g xmlns="{NS}" id="pq-equipment">
    <g id="pq-head" class="hidden" transform="translate({hx-25},{hy-12})">
      <path d="M3 10 Q4 -10 24 -10 Q43 -10 45 10Z" fill="#c4b5fd" stroke="#592512" stroke-width="3"/>
      <rect x="-2" y="8" width="52" height="8" rx="4" fill="#e9d5ff" stroke="#592512" stroke-width="3"/>
    </g>
    <g id="pq-face" class="hidden" fill="none" stroke="#592512" stroke-width="2.5">
      <ellipse cx="{lx}" cy="{ly}" rx="13" ry="12"/>
      <ellipse cx="{rx}" cy="{ry}" rx="13" ry="12"/>
      <path d="M{lx+13} {ly} Q{(lx+rx)/2} {ly-6} {rx-13} {ry} M{lx-13} {ly} l-10 -4 M{rx+13} {ry} l10 -4"/>
    </g>
    <g id="pq-neck" class="hidden">
      <path d="M{nx} {ny} Q{(nx+nright)/2} {ny+13} {nright} {ny} l0 7 Q{(nx+nright)/2} {ny+20} {nx} {ny+7}Z" fill="#f9a8d4" stroke="#592512" stroke-width="2"/>
      <circle cx="{(nx+nright)/2}" cy="{ny+16}" r="4" fill="#facc15" stroke="#592512" stroke-width="1.6"/>
    </g></g>'''
    # All three follow head and whole-body animation matrices, including sleeping.
    head.append(E.fromstring(equipment))
    return E.tostring(root,encoding='unicode')
