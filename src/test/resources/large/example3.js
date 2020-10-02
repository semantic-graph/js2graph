var child_process_1 = require("child_process");
var SfdxUtils = function $SfdxUtils$() {
};
SfdxUtils.execSfdx = function $SfdxUtils$execSfdx$($command$$, $targetusername$$) {
  return "undefined" != typeof $targetusername$$ ? child_process_1.execSync("sfdx " + $command$$ + " --targetusername " + $targetusername$$).toString() : child_process_1.execSync("sfdx " + $command$$).toString();
};

/*
WALA IR of ?:
5   v9 = global:global require                              example3.js [22->29] (line 1)
7   v11 = global:global __WALA__int3rnal__global            example3.js [22->46] (line 1)
8   v7 = invoke v9@8 v11,v12:#child_process exception:v13   example3.js [22->46] (line 1)
9   lexical:child_process_1@Lexample3.js = v7               example3.js [0->47]  (line 1)

WALA IR of $SfdxUtils$execSfdx$:

0   v5 = new <JavaScriptLoader,LArray>@0     example3.js [113->390] (line 4) [5=[arguments]]
1   v9 = typeof(v4)                          example3.js [200->224] (line 5) [4=[$targetusername$$]]
2   v7 = binaryop(ne) v8:#undefined , v9     example3.js [185->224] (line 5)
3   conditional branch(eq, to iindex=21) v7,v10:#0  example3.js [185->387] (line 5)
BB2
8   v17 = lexical:child_process_1@Lexample3.js  example3.js [227->242] (line 5) [17=[$$destructure$rcvr3]]
9   check v17                                example3.js [227->242] (line 5) [17=[$$destructure$rcvr3]]
BB3
12   v22 = binaryop(add) v23:#sfdx  , v3     example3.js [252->272] (line 5) [3=[$command$$]]
13   v21 = binaryop(add) v22 , v24:# --targetusername example3.js [252->295] (line 5)
14   v20 = binaryop(add) v21 , v4            example3.js [252->315] (line 5) [4=[$targetusername$$]]
15   v19 = dispatch v18:#execSync@15 v17,v20 exception:v25example3.js [227->316] (line 5) [19=[$$destructure$rcvr2]18=[$$destructure$elt5]17=[$$destructure$rcvr3]]
BB4
18   v27 = dispatch v26:#toString@18 v19 exception:v28  example3.js [227->327] (line 5) [26=[$$destructure$elt4]19=[$$destructure$rcvr2]]
BB5
20   goto (from iindex= 20 to iindex = 35)   example3.js [185->387] (line 5)
BB6
25   v33 = lexical:child_process_1@Lexample3.js example3.js [330->345] (line 5) [33=[$$destructure$rcvr5]]
26   check v33                               example3.js [330->345] (line 5) [33=[$$destructure$rcvr5]]
BB7
29   v35 = binaryop(add) v23:#sfdx  , v3     example3.js [355->375] (line 5) [3=[$command$$]]
30   v34 = dispatch v18:#execSync@30 v33,v35 exception:v36example3.js [330->376] (line 5) [34=[$$destructure$rcvr4]18=[$$destructure$elt5]33=[$$destructure$rcvr5]]
BB8
33   v37 = dispatch v26:#toString@33 v34 exception:v38  example3.js [330->387] (line 5) [26=[$$destructure$elt4]34=[$$destructure$rcvr4]]
BB9
BB10
           v6 = phi  v27,v37
35   return v6                               example3.js [178->388] (line 5)
BB11

 */
