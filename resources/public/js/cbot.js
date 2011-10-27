
var cbotGlobal={};
cbotGlobal.NV="unknown";
cbotGlobal.lastUUID=cbotGlobal.NV;
cbotGlobal.app=cbotGlobal.NV;
cbotGlobal.inst=cbotGlobal.NV;
cbotGlobal.firstLoaded=true;
cbotGlobal.states={};
cbotGlobal.idx2key=[];

cbotGlobal.lastUUID="unknown";
cbotGlobal.lastState=null;


cbotGlobal.etiqueta=[];
cbotGlobal.etiqueta[0]="state";
cbotGlobal.etiqueta[1]="result";
cbotGlobal.etiqueta[2]="when";
cbotGlobal.etiqueta[3]="delta-micro";

function instanceChange() {
  cbotGlobal.inst=jQuery("#instances option:selected").text();
  startMonitoring();
}

function fillSelect(json,tagLabel,globalName,changeFunc,nextFunc) {
  var combo=jQuery(tagLabel).empty();
  var selected=cbotGlobal.NV;

  for (i=0; i<json.length; i+=1) {
    if (i===0) {
      combo.prepend('<option value="'+json[i]+'" selected="selected">'+json[i]+'</option>');
      selected=json[i];
    }
    else {
      combo.prepend('<option value="'+json[i]+'">'+json[i]+'</option>');
    }
  }

  if (nextFunc) {
    nextFunc(selected);
  }
  if (changeFunc) {
    jQuery(tagLabel).change(changeFunc);
  }
  if (globalName) {
    cbotGlobal[globalName]=selected;
  }
}

function fillInstances(instancesStr) {
  fillSelect(instancesStr,"#instances","inst",instanceChange,null );
  if (cbotGlobal.firstLoaded) {
    cbotGlobal.firstLoaded=false;
    applicationChange();
    instanceChange();
  }
  else {
    startMonitoring();
  }
}

function applicationChange() {
  var app=jQuery("#applications option:selected").text();
  jQuery.ajax({
    url: "/apps/"+app,
    dataType: "json",
    success: function (instancesStr) {
      fillInstances(instancesStr);
      //startMonitoring();
    }
  });
  cbotGlobal.app=app;
  cbotGlobal.inst=cbotGlobal.NV;
  
  jQuery.ajax({
    url: "/conf/"+app,
    dataType: "json",
    success: buildWorkspace
  });
}

function fillApplications(apps) {
  fillSelect(apps,"#applications","app",applicationChange,function (selected) {
    jQuery.ajax({
      url: "/apps/"+selected,
      dataType: "json",
      success: fillInstances});
  });
}

function fillOperations(oprs) {
  fillSelect(oprs,"#operations");
}

function each(set,fun) {
  var i;
  for (i=0;i<set.length;i++) {
    fun(set[i]);
  }
}

function removeArrows(state,otherKey) {
  for (i=0; i<state.statesOut.length; i++) {
    if (state.statesOut[i]===otherKey || otherKey==="*") {
      each(state.arrowsOut[i],function(elem){elem.remove()});
      state.tipsOut[i].remove();
    }
  }  
}

function dragStateStart() {
  var state=null;
  if (!isNaN(this.idx)) {
    state=cbotGlobal.states[cbotGlobal.idx2key[this.idx]];
  }
  if (state) {
    var i;
    for (i=0; i<state.r_t_i_set.items.length;i++) {
      state.r_t_i_set[i].ox=state.r_t_i_set[i].attr("x");
      state.r_t_i_set[i].oy=state.r_t_i_set[i].attr("y");
    }
//desconectar elemento
    removeArrows(state,"*");
    each(state.statesIn,function (name) {
      var other=cbotGlobal.states[name];
      removeArrows(other,state.key);
    });
  }
}

function dragStateStop() {
  var state=null;
  if (!isNaN(this.idx)) {
    state=cbotGlobal.states[cbotGlobal.idx2key[this.idx]];
  }
  if (state) {
    var i;
    for (i=0; i<state.r_t_i_set.items.length;i++) {
      delete(state.r_t_i_set[i].ox);
      delete(state.r_t_i_set[i].oy);
    }
    cbotGlobal.conf.states[state.conf_idx].flow.x=state.r_t_i_set[0].attrs.x;
    cbotGlobal.conf.states[state.conf_idx].flow.y=state.r_t_i_set[0].attrs.y;
    reConnectStates(state,"*");
    each(state.statesIn,function(name) {
      var other=cbotGlobal.states[name];
      reConnectStates(other,state.key);
    });
  }
}

function dragStateMove(dx,dy) {
  var g=null;
  if (!isNaN(this.idx)) {
    g=cbotGlobal.states[cbotGlobal.idx2key[this.idx]];
  }
  if (g) {
    var x;
    for (x=0; x<g.r_t_i_set.items.length; x++) {
      var obj=g.r_t_i_set[x];
      obj.attr({ x: obj.ox + dx, y: obj.oy + dy });
    }
  }
  //connectStates(cbotGlobal.conf);
}


jQuery(document).ready(function() {
  jQuery("#start-button").click(function () {
    if (cbotGlobal.app!==cbotGlobal.NV && cbotGlobal.inst!==cbotGlobal.NV) {
      startInstance(cbotGlobal.inst);
    }
    else {
      alert("first you must select application and instance !");
    }
  });
  jQuery("#stop-button").click(function () {
    if (cbotGlobal.app!==cbotGlobal.NV && cbotGlobal.inst!==cbotGlobal.NV) {
      stopInstance(cbotGlobal.inst);
    } 
    else {
      alert("first you must select application and instance !");
    }
  });
  jQuery("#resume-button").click(function () {
    if (cbotGlobal.app!==cbotGlobal.NV && cbotGlobal.inst!==cbotGlobal.NV) {
      resumeInstance(cbotGlobal.inst);
    }
    else {
      alert("first you must select application and instance !");
    }
  });
  jQuery("#add-state").click(function() {
    if (cbotGlobal.app!==cbotGlobal.NV) {
      var stateName=jQuery("#state-name").val();
      var stateOpr=jQuery("#operations option:selected").text();
      var newState={flow: {x:500,y:300,connect:[]}, key:stateName,
                    "conf-map":{opr: stateOpr, conf: []}};

      //falta aumentar el nuevo al conf global!!!!! 
      buildState(cbotGlobal.conf.states.length,newState);
    }
  });

  jQuery.ajax({
    url: "/operations",
    dataType: "json",
    success: fillOperations
  });

  var apps=jQuery("#applications");
  apps.empty();   
  jQuery.ajax({
    url: "/apps",
    dataType: "json",
    success: fillApplications});
});


function startInstance(instName) {
  cbotGlobal.lastUUID="unknown";
  jQuery.ajax({
    url: "/apps/"+cbotGlobal.app+"/"+cbotGlobal.inst,
    //dataType: "json",
    data: {"cmd":"start"},
    success: function(result) {
      startMonitoring();  
    }
  });
  
}

function stopInstance(instName) {
  jQuery.ajax({
    url: "/apps/"+cbotGlobal.app+"/"+cbotGlobal.inst,
    dataType: "json",
    data: {"cmd":"stop"},
    success: function(result) {
      startMonitoring();  
    }
  });
}

function resumeInstance(instName) {
//  var msg=document.getElementById("resume-msg").value;
  var msg=jQuery("#resume-msg").val();
  jQuery.ajax({
    url: "/apps/"+cbotGlobal.app+"/"+cbotGlobal.inst,
    dataType: "json",
    data: {"cmd":"resume",
           "msg":msg},
    success: function(result) {
      startMonitoring();
    }
  });
}

////////////////////////////////////////////////////////



Raphael.fn.arrow = function (x1, y1, x2, y2, size) {
    var angle = Math.atan2(x1-x2,y2-y1);
    angle = (angle / (2 * Math.PI)) * 360;
    var cx=(x1+x2)/2;
    var cy=(y1+y2)/2;
    var arrowPath = this.path("M" + cx + " " + cy + " L" + (cx - size) + " " 
                             + (cy - size) + " L" + (cx - size) + " " + (cy + size) 
                             + " L" + cx + " " 
                             + cy ).attr("fill","black").rotate((90+angle),cx,cy);
    var linePath = this.path("M" + x1 + " " + y1 + " L" + x2 + " " + y2).toBack();
    return [linePath,arrowPath];
}

function buildState(index,state) {
  var txt=cbotGlobal.workspace.text(75,50,state.key);
  var icono=cbotGlobal.workspace.image("/images/"+state["conf-map"].opr+".gif",40,50,15,15);
  var g=cbotGlobal.workspace.set(txt,icono);
  var bbox=g.getBBox();
  var rect=cbotGlobal.workspace.rect(bbox.x-8,bbox.y-2,bbox.width+16,bbox.height+4,8);
  rect.attr({"stroke": "#007eaf",
             "stroke-width": 3,
             "fill": "#bbbbbb"}).toBack();  
  g=cbotGlobal.workspace.set(rect,txt,icono);//.data("dim",rect.getBBox());
  
  g.animate({x:state.flow.x, y:state.flow.y},2000,"bounce",function () {
    this[1].animate({x: this[1].attrs.x+15+bbox.width/2,y: this[1].attrs.y+18},1000,"bounce").toFront();
    this[2].animate({x: this[2].attrs.x+4,y: this[2].attrs.y+8},1000,"bounce").toFront();
  });
  rect.idx=cbotGlobal.idx2key.length;//le pegamos su indice al rect
  //para el drag!!
  cbotGlobal.idx2key.push(state.key);
  cbotGlobal.states[state.key]={"key":state.key,
                                "r_t_i_set": g, 
                                "conf_idx": index, 
                                "statesOut": [],
                                "arrowsOut": [],
                                "tipsOut": [],
                                "statesIn": []};//statesIn los nombres
                                                //de los estados que sales hacia ACA
  g.drag(dragStateMove,dragStateStart,dragStateStop);
  return g;
}

function labelIt(x,y,txt,elem) {
  var condition=cbotGlobal.workspace.text(x,y,txt);
  var bbox=condition.getBBox();
  var rr=cbotGlobal.workspace.rect(bbox.x-2,bbox.y-2,bbox.width+4,bbox.height+4);
  rr.attr({fill: "#fff"});
  var tip=cbotGlobal.workspace.set(rr,condition);
  tip.attr({opacity: 0}).toFront();    
  elem.hover(function() {tip.animate({opacity: 1}, 500)},
             function() {tip.animate({opacity: 0}, 500)});
  return tip;
}

function connectUsing(index,state,other,outNum,tipTxt) {
  pt=[state.r_t_i_set[0].attrs.x+state.r_t_i_set[0].attrs.width/2,
      state.r_t_i_set[0].attrs.y+state.r_t_i_set[0].attrs.height/2,
      other.r_t_i_set[0].attrs.x+other.r_t_i_set[0].attrs.width/2,         
      other.r_t_i_set[0].attrs.y+other.r_t_i_set[0].attrs.height/2]

  arr=cbotGlobal.workspace.arrow(pt[0],pt[1],pt[2],pt[3],4);
  var tip=labelIt((pt[0]+pt[2])/2,
                  (pt[1]+pt[3])/2,
                  outNum+" ["+tipTxt+"]",
                  state.r_t_i_set);
  if (index<0) {
    state.statesOut.push(other.key);
    state.arrowsOut.push(arr);
    other.statesIn.push(state.key);
    state.tipsOut.push(tip);
  }
  else {
    state.arrowsOut[index]=arr;
    state.tipsOut[index]=tip;
    
  }
}

function reConnectStates(state,otherName) {
  var i;
  for (i=0;i<state.statesOut.length;i++) {
    var other=cbotGlobal.states[state.statesOut[i]];
    if (other.key===otherName || otherName==="*") {
      var tipTxt=cbotGlobal.conf.states[state.conf_idx].flow.connect[i*2+1]!==undefined?
        cbotGlobal.conf.states[state.conf_idx].flow.connect[i*2+1]:"default";
      connectUsing(i,state,other,i+1,tipTxt)
    }
  }
}

function connect(state,other,outNum,tipTxt) {
  connectUsing(-1,state,other,outNum,tipTxt);
}

function connectStates() {
  var i,j;
  var state,other;
  var connectArr;
  var arr;
  var pt;
  for (i=0; i<cbotGlobal.conf.states.length; i+=1) {
    state=cbotGlobal.states[cbotGlobal.conf.states[i].key];
/*
    if (state.arrowsOut) {
      for (j=0; j<state.arrowsOut.length; j+=1) {
        arr=state.arrowsOut[j];
        arr[0].remove();
        arr[1].remove();
      }
    }
    if (state.tipsOut) {
      for (j=0; j<state.tipsOut.length; j+=1) {
        arr=state.tipsOut[j];
        arr[0].remove();
        arr[1].remove();
      }
    }
*/
    connectArr=cbotGlobal.conf.states[state.conf_idx].flow.connect;
    for (j=0; j<connectArr.length; j+=2) {
      other=cbotGlobal.states[connectArr[j]];
      connect(state,other,Math.floor((j+1)/2+1),connectArr[j+1]!==undefined?connectArr[j+1]:"default");
    }
  }
  startMonitoring();
}

function buildWorkspace(conf) {
  var i;
  cbotGlobal.conf=conf;
  jQuery("#states").empty();
  cbotGlobal.workspace=Raphael("states","600","400");
  var rStart=cbotGlobal.workspace.image("/images/robot-start.gif",40,50,15,15).toFront().attr({opacity: 0});  
  var rStop=cbotGlobal.workspace.image("/images/robot-stop.gif",40,50,15,15).toFront().attr({opacity: 0});
  var rWaiting=cbotGlobal.workspace.image("/images/robot-waiting.gif",40,50,15,15).toFront().attr({opacity: 0});  
  var robot=cbotGlobal.workspace.set(rStart,rStop,rWaiting);
  cbotGlobal.robot=robot;
  cbotGlobal.states={};
  cbotGlobal.idx3key=[];
  for (i=0; i<conf.states.length; i+=1) {
    buildState(i,conf.states[i]);
  }
  setTimeout(function() {connectStates()},3000);
}

function startMonitoring() {
  jQuery.ajax({
    url:"/apps/"+cbotGlobal.app+"/"+cbotGlobal.inst,
    dataType: "json",
    data: {cmd: "current-pos",
           uuid: cbotGlobal.lastUUID,
           timeout: "20000"},
    success: handler1});
}

function showRobot(idx) {
  var i;
  if (cbotGlobal.robot !== undefined) {
    if (cbotGlobal.robot[idx].attrs.opacity!==1) { // || true
      for (i=0; i<3; i+=1) {
        cbotGlobal.robot[i].attr({opacity: 0});
      }
      cbotGlobal.robot[idx].animate({opacity: 1},500);
    }  
  }
}

function handler1(result) {
  if (cbotGlobal.states.length === 0) return;
  var thumbUp=$("#thumb-up");
  var thumbDn=$("#thumb-down");
  if (result.app === cbotGlobal.app && result.inst === cbotGlobal.inst) {
    cbotGlobal.lastUUID=result.uuid;
    thumbUp.hide();
    thumbDn.hide();  
    if (result.status === "bad") {
      thumbDn.show();
    }
    else {
      thumbUp.show();
    }
    if (!result["stop?"]) {
      if (result["awaiting?"]) {
        showRobot(2);
      }
      else {
        showRobot(0);
      }
    }
    else {
      showRobot(1);
    }

    if (cbotGlobal.lastState !== null) {  
      cbotGlobal.lastState.r_t_i_set[0].animate({"fill": "#bbbbbb", 
                                     "stroke-width": 3,
                                     "stroke": "#007eaf", 
                                     },1000);
    }
    cbotGlobal.lastState=cbotGlobal.states[result.current];
    cbotGlobal.lastState.r_t_i_set[0].animate({fill: "#eeeeee", 
                                   stroke: "#c42530",
                                   "stroke-width": 3},1000);    

    var xx=parseInt(cbotGlobal.states[result.current].r_t_i_set[0].attrs.x,10); //
    var yy=parseInt(cbotGlobal.states[result.current].r_t_i_set[0].attrs.y,10); //
    

    cbotGlobal.robot.animate({x: xx+30,y: yy},500);
    var resultIndex=0;
    var x=jQuery("table.status").find("tr").each(function (i) {
      if (i>0) {
        $(this).find("td").each(function (j) {
        //if (resultIndex<result["stats"]["info"].length
          if (resultIndex<result.stats.info.length) { 
            $(this).text(result.stats.info[resultIndex][cbotGlobal.etiqueta[j]]);
          }
          else {
            $(this).text("");
          }   
        });
        resultIndex+=1;
      }
    });
    if (!result["stop?"]) {
      startMonitoring();
    }   
  }

}


