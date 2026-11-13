"""Original low-poly room props, fixed scene units, no textures or external assets."""
from generate_dev_accessories import generate

def main():
    generate('bed','BED',[0,0,0],[.38,.61,.76,1],lambda s,r,c,b:(
        b((0,.07,0),(.30,.07,.20)),b((0,.19,0),(.29,.04,.19)),
        b((0,.26,-.19),(.32,.14,.035)),b((0,.245,-.10),(.20,.025,.065))),folder='furniture',attachment=False)
    generate('lamp','LAMP',[0,0,0],[.88,.72,.35,1],lambda s,r,c,b:(
        c(.13,0,.035),c(.022,.035,.66),c(.17,.695,.22)),folder='furniture',attachment=False)
    generate('carpet','CARPET',[0,0,0],[.57,.64,.78,1],lambda s,r,c,b:
        b((0,.012,0),(.95,.012,.50)),folder='furniture',attachment=False)

if __name__=='__main__': main()
