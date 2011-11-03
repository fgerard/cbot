// jsROBOT Viewer
// InterWare de Mexico, S.A. de C.V.
// Felipe Gerard

var cbotGlobal={};
cbotGlobal.NV="unknown";
cbotGlobal.lastUUID=cbotGlobal.NV;
cbotGlobal.app=cbotGlobal.NV;
cbotGlobal.inst=cbotGlobal.NV;
cbotGlobal.states={};
//cbotGlobal.idx2key=[];
cbotGlobal.counter=0;
cbotGlobal.monitoring=true;

cbotGlobal.RUNNING_ROBOT=0;
cbotGlobal.STOP_ROBOT=1;
cbotGlobal.WAIT_ROBOT=2;

cbotGlobal.lastState=null;

cbotGlobal.etiqueta=[];
cbotGlobal.etiqueta[0]="state";
cbotGlobal.etiqueta[1]="result";
cbotGlobal.etiqueta[2]="when";
cbotGlobal.etiqueta[3]="delta-micro";

cbotGlobal.oprDiags={};

cbotGlobal.lastUUID=localUUID();

function localUUID() {
  return Raphael.createUUID();
}

function instanceChange() {
  showRobot(-1);
  cbotGlobal.inst=jQuery("#instances option:selected").text();
  startMonitoring(true);
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
  if (changeFunc) {
    jQuery(tagLabel).change(changeFunc);
  }
  if (globalName) {
    cbotGlobal[globalName]=selected;
  }
  if (nextFunc) {
    nextFunc(selected);
  }
  return selected;
}

function fillInstances(instances) {
  cbotGlobal.inst=fillSelect(instances,"#instances","inst",instanceChange,null );
}

function applicationChange() {
  showRobot(-1);
  var app=jQuery("#applications option:selected").text();
  cbotGlobal.app=app;
  cbotGlobal.inst=cbotGlobal.NV;
  jQuery.ajax({
    url: "/apps/"+app,
    dataType: "json",
    success: function (instancesStr) {
      fillInstances(instancesStr);
    }
  });
  jQuery.ajax({
    url: "/conf/"+app,
    dataType: "json",
    success: buildWorkspace
  });
  //startMonitoring(true);
}

function fillApplications(apps) {
  var app=fillSelect(apps,"#applications","app",applicationChange,function (selected) {
    jQuery.ajax({
      url: "/apps/"+selected,
      dataType: "json",
      success: fillInstances});
  });
  cbotGlobal.app=app;
  cbotGlobal.inst=cbotGlobal.NV;

  jQuery.ajax({
    url: "/conf/"+app,
    dataType: "json",
    success: buildWorkspace
  })
}

function fillOperations(oprs) {
  fillSelect(oprs,"#operations");
}

function setMonitoring(monitoring) {
  cbotGlobal.monitoring=monitoring;
  if (!monitoring) {
    showRobot(-1);
  }
}

function each(set,fun) {
  var i;
  for (i=0;i<set.length;i++) {
    fun(set[i]);
  }
}

function removeArrows(state,otherKey) {
  var removed=[];
  var i=0;
  for (i=0; i<state.statesOut.length; i++) {
    if (state.statesOut[i]===otherKey || otherKey==="*") {
      each(state.arrowsOut[i],function(elem){
        elem.remove();
      });
      if (state.tipsOut[i]!==undefined) state.tipsOut[i].remove();
      removed.push([state.key,state.statesOut[i]]);
    }
  }
  return removed;
}

function getCenter(stateName) {
  var state=cbotGlobal.states[stateName];
  var bbox=state.r_t_i_set[0].getBBox();
  var x=Math.floor(bbox.x+bbox.width/2);
  var y=Math.floor(bbox.y+bbox.height/2);
  return {"x":x, "y":y};
}

function createPathStr(from,to) {
  var f=from;
  var t=to;
  return "M"+from.x+","+from.y+"L"+to.x+","+to.y;
}

cbotGlobal.dragging=false;
cbotGlobal.connecting=false;

function dragStateStart(evt) {
  if (cbotGlobal.dragging || cbotGlobal.connecting) return;
  var state=null;
  if (this.group!==undefined) {
    cbotGlobal.dragging=true;
    state=cbotGlobal.states[this.group[0].key];
  }
  if (state) {
    removeGlobal("connectImage");
    removeGlobal("connectingPath");
    var i;
    for (i=0; i<state.r_t_i_set.length;i++) {
      state.r_t_i_set[i].ox=parseInt(state.r_t_i_set[i].attrs.x,10);
      state.r_t_i_set[i].oy=parseInt(state.r_t_i_set[i].attrs.y,10);
    }
//desconectar elemento
    var arrowsOut=removeArrows(state,"*");
    var arrowsIn=[];
    each(state.statesIn,function (name) {
      var other=cbotGlobal.states[name];
      var arrows=removeArrows(other,state.key);
      each(arrows,function(par) {arrowsIn.push(par)});
    });
    var pathsOut=[];
    each(arrowsOut,function(par) {
      var from=getCenter(par[0]); from.x0=from.x; from.y0=from.y;
      var to=getCenter(par[1]); to.x0=to.x; to.y0=to.y;
      pathsOut.push({"from": from,"to":to,"path":cbotGlobal.workspace.path(createPathStr(from,to)).toBack().attr({"stroke":"#c42530"})})
    });
    var pathsIn=[];
    each(arrowsIn,function(par) {
      var from=getCenter(par[0]); from.x0=from.x; from.y0=from.y;
      var to=getCenter(par[1]); to.x0=to.x; to.y0=to.y;
      pathsIn.push({"from": from,"to":to,"path":cbotGlobal.workspace.path(createPathStr(from,to)).toBack().attr({"stroke":"#007eaf"})})
    });
    state.pathsOut=pathsOut;
    state.pathsIn=pathsIn;
  }
}

function dragStateStop() {
  if (!cbotGlobal.dragging || cbotGlobal.connecting) return;
  var state=null;
  if (this.group!==undefined) {
    state=cbotGlobal.states[this.group[0].key];
  }
  if (state) {
    var i;
    for (i=0; i<state.r_t_i_set.length;i++) {
      delete(state.r_t_i_set[i].ox);
      delete(state.r_t_i_set[i].oy);
    }
    cbotGlobal.conf.states[state.conf_idx].flow.x=state.r_t_i_set[0].attrs.x;
    cbotGlobal.conf.states[state.conf_idx].flow.y=state.r_t_i_set[0].attrs.y;
    each(state.pathsIn,function(info) {info.path.remove()});
    each(state.pathsOut,function(info) {info.path.remove()});
    reConnectStates(state,"*");
    each(state.statesIn,function(name) {
      var other=cbotGlobal.states[name];
      reConnectStates(other,state.key);
    });
    cbotGlobal.dragging=false;
  }
}

function dragStateMove(dx,dy) {
  if (!cbotGlobal.dragging || cbotGlobal.connecting) return;
  var state=null;
  if (this.group!==undefined) {
    state=cbotGlobal.states[this.group[0].key];
  }
  if (state) {
    var x;
    for (x=0; x<state.r_t_i_set.length; x++) {
      var obj=state.r_t_i_set[x];
      obj.attr({ x: obj.ox + dx, y: obj.oy + dy });
    }
    each(state.pathsOut,function(info) {
      info.from.x=info.from.x0+dx;
      info.from.y=info.from.y0+dy;
      info.path.attr({"path": createPathStr(info.from,info.to)});
    });
    each(state.pathsIn,function(info) {
      info.to.x=info.to.x0+dx;
      info.to.y=info.to.y0+dy;
      info.path.attr({"path": createPathStr(info.from,info.to)});
    });
  }
}

function startInstance(instName) {
  jQuery.ajax({
    url: "/apps/"+cbotGlobal.app+"/"+cbotGlobal.inst,
    //dataType: "json",
    data: {"cmd":"start"},
    success: function(result) {
      jQuery("#result-str").text(result);
      startMonitoring(false);
    }
  });
  jQuery("#start-button").hide();
}

function stopInstance(instName) {
  jQuery.ajax({
    url: "/apps/"+cbotGlobal.app+"/"+cbotGlobal.inst,
    //dataType: "json",
    data: {"cmd":"stop"},
    success: function(result) {
      jQuery("#result-str").text(result);
      startMonitoring(false);  
    }
  });
  jQuery("#stop-button").hide();
}

function resumeInstance(instName) {
  var msg=jQuery("#resume-msg").val();
  jQuery.ajax({
    url: "/apps/"+cbotGlobal.app+"/"+cbotGlobal.inst,
    //dataType: "json",
    data: {"cmd":"resume",
           "msg":msg},
    success: function(result) {
      jQuery("#result-str").text(result);
      startMonitoring(false);
    }
  });
  jQuery("#resume").hide();
}

function saveStates() {
  jQuery.ajax({
    type: "POST",
    url: "/store/"+cbotGlobal.app,
    dataType: "json",
    data:{"conf":cbotGlobal.conf},
    success: function(result) {
      alert("result:"+result.result);
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
                             + cy ).attr("fill","black").rotate((90+angle),cx,cy).toBack();
    var linePath = this.path("M" + x1 + " " + y1 + " L" + x2 + " " + y2).toBack();
    return [linePath,arrowPath];
}

/*
Array.fn.insert = function(value) {
  var i;
  for (i=this.length; i>0; i--) {
    this[i]=this[i-1];
  }
  this[0]=value;
};


Array.method("insert",function (value){
  var i;
  for (i=this.length; i>0; i--) {
    this[i]=this[i-1];
  }
  this[0]=value;
});
*/

function connectInConf(state,other,regExp) {
  var idx=state.conf_idx;
  var connectArr=cbotGlobal.conf.states[idx].flow.connect;
  var i;
  if (connectArr.length===0) {
    connectArr.unshift(other.key);
  }
  else {
    connectArr.unshift(regExp);
    connectArr.unshift(other.key);
  }
}

function clearEvents() {
  var state=cbotGlobal.states[":pagina"];
  state.r_t_i_set.unmouseover(mouseIsOver);
  state.r_t_i_set.unmouseout(mouseIsOut);
  //removeArrows(state,"*");

  //var i;
  //for (i=0; i<state.arrowsOut.length;i++) {
    //state.arrowsOut[i][0].remove();
  //}
}

function connectingStart(x,y,event) {
  if (cbotGlobal.connecting) return;
  cbotGlobal.connecting=true;

  var bbox=cbotGlobal.connectImageBBOX;
  cbotGlobal.offsetX=event.offsetX-(bbox.x+bbox.width);
  cbotGlobal.offsetY=event.offsetY-(bbox.y+bbox.height);
  cbotGlobal.connectingPath=cbotGlobal.workspace.path("M"+(bbox.x+bbox.width)+" "+(bbox.y+bbox.height)); //+"l0 0"
}

function connectingStop() {
  cbotGlobal.workspace.renderfix(); 
  cbotGlobal.connectingPath.remove();
  if (cbotGlobal.otherState!==null) {
    var state=cbotGlobal.connectingState;
    var other=cbotGlobal.otherState;
    if (contains(state.statesOut,other.key)>=0) {//ya esta conectado !!
    }
    else {
      //alert("connecting "+state.key+" -> "+other.key);
      connectInConf(state,other,"undefined"); 
      //connect(state,other,1,"undefined");
      removeArrows(state,"*");
      state.statesOut.unshift(other.key);
      reConnectStates(state,"*");
      other.statesIn.push(state.key);
    }  
  }
  removeGlobal("otherGlow");
  cbotGlobal.connectingPath.remove();
  cbotGlobal.connectingPath=null;
  cbotGlobal.connectImage.remove();
  cbotGlobal.connectImage=null;
  cbotGlobal.connectImageBBOX=null;
  cbotGlobal.otherState=null;
  cbotGlobal.connecting=false;
}

function connectingMove(dx,dy) {
  var bbox=cbotGlobal.connectImageBBOX;
  cbotGlobal.connectingPath.attr({"path":"M"+(bbox.x+bbox.width)+" "+(bbox.y+bbox.height)+"l"+(dx+cbotGlobal.offsetX)+" "+(dy+cbotGlobal.offsetY)});
}

function contains(arr,elem) {
  var i;
  for (i=0; i<arr.length; i++) {
    if (arr[i]===elem) {
      return i;
    }
  }
  return -1;
}

cbotGlobal.connectImage=null;
cbotGlobal.connectingPath=null;
cbotGlobal.connectImageBBOX=null;
cbotGlobal.connectingState=null;
cbotGlobal.otherState=null;

cbotGlobal.otherGlow=null;

function removeGlobal(name) {
  if (cbotGlobal[name]!==null) {
    cbotGlobal[name].remove();
    cbotGlobal[name]=null;
  }
}

function mouseIsOver() {
  if (cbotGlobal.dragging) return; 
  if (cbotGlobal.connectImage!==null) {
    if (cbotGlobal.connectingPath!==null) {
      if (cbotGlobal.connectImageBBOX!==this.group[0].getBBox()) {
        // estamos conectando y ahora sobre OTRO estado
        cbotGlobal.otherState=cbotGlobal.states[this.group[0].key];
        removeGlobal("otherGlow");
        cbotGlobal.otherGlow=this.group[0].glow();
        return;
      }
      else {
        removeGlobal("otherGlow");
        cbotGlobal.otherState=null;
        return;
      }
    }
    else {
      removeGlobal("connectImage");
    }
  }
  var bbox=this.group[0].getBBox();
  var p=cbotGlobal.workspace.path("M"+(bbox.x+bbox.width)+" "+(bbox.y+bbox.height)+"l -6 -2 -6 2 2 -6 -4 -2 4 -2 -2 -6 6 2 6 -2 -2 6 4 2 -4 2 z").attr({"fill":"#ff0000"});
  cbotGlobal.connectImage=p
  cbotGlobal.connectImageBBOX=bbox;
  cbotGlobal.connectImage.drag(connectingMove,connectingStart,connectingStop);
  cbotGlobal.connectingState=cbotGlobal.states[this.group[0].key];
}

function mouseIsOut() {
  if (this.type="rect") {
    removeGlobal("otherGlow");
    cbotGlobal.otherState=null;
  }
}

function setTimeoutOpr(confState,div) {
  div.find("#timeout").val(confState["conf-map"].timeout);
  div.find("#retry-delay").val(confState["conf-map"]["retry-delay"]);
  div.find("#retry-count").val(confState["conf-map"]["retry-count"]);
}

cbotGlobal.oprSetFunc={};

cbotGlobal.oprSetFunc["sleep-opr"]=function (state,confState) {
  var div=$("#sleep-oprDialog");
  div.find("#state-name").val(state.key);
  div.find("#delta").val(confState["conf-map"].conf.delta);
}

cbotGlobal.oprSetFunc["socket-opr"]=function(state,confState) {
  var div=$("#socket-oprDialog");
  div.find("#state-name").val(state.key);
  setTimeoutOpr(confState,div);

  div.find("#host").val(confState["conf-map"].conf.host);
  div.find("#port").val(confState["conf-map"].conf.port);
}


function updateState() {
  var state=cbotGlobal.states[this.group[0].key];
  var confState=cbotGlobal.conf.states[state.conf_idx];
  var opr=confState["conf-map"].opr;
  cbotGlobal.oprSetFunc[opr](state,confState);
  getDialog4Opr(opr).dialog("open");
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
  g.attr({x:parseInt(state.flow.x,10), y:parseInt(state.flow.y,10)});
  g[1].attr({x: parseInt(g[1].attrs.x,10)+15+bbox.width/2,
             y: parseInt(g[1].attrs.y,10)+18}).toFront();
  g[2].attr({x: parseInt(g[2].attrs.x,10)+4,
             y: parseInt(g[2].attrs.y,10)+7}).toFront();
            
//  rect.idx=cbotGlobal.idx2key.length;//le pegamos su indice al rect
//  cbotGlobal.idx2key.push(state.key);
  rect.key=state.key; 

//  g[0].glower=g[0];
  g[0].group=g
//  g[0].connectOpr=false; // initialy is dragging
//  g[2].glower=g[2];
  g[2].group=g;
  g[1].group=g;
//  g[1].glower=g[0];
  
  //para el drag!!
  
  cbotGlobal.states[state.key]={"key":state.key,
                                "r_t_i_set": g, 
                                "conf_idx": index, 
                                "statesOut": [],
                                "arrowsOut": [],
                                "tipsOut": [],
                                "statesIn": []//, "glowArrow":null
                              };//statesIn los nombres
                                                //de los estados que salen hacia ACA

  g.drag(dragStateMove,dragStateStart,dragStateStop);
  //g.click(editStateConf);

  //g[0].hover(glowIcon,unGlowIcon);
  g.mouseover(mouseIsOver);
  g.mouseout(mouseIsOut);
  g.dblclick(updateState);
/*
  g.dblclick(function(){
    if (cbotGlobal.connectImage !== null) {
      cbotGlobal.connectImage.remove();
      cbotGlobal.connectImage=null;
    }
    else {
      var g=this.glower.glow();
      cbotGlobal.connectImage=g; 
      this.group[0].connectOpr=this.type==="image"; //connecting or dragging
    }
  });
  */
  return g;
}

function labelIt(x,y,txt,elem) {
  var condition=cbotGlobal.workspace.text(x,y,txt);
  var bbox=condition.getBBox();
  var rr=cbotGlobal.workspace.rect(bbox.x-2,bbox.y-2,bbox.width+4,bbox.height+4);
  rr.attr({fill: "#fff"});
  var tip=cbotGlobal.workspace.set(rr,condition);
  tip.attr({opacity: 0}).toBack();//toFront();    
  elem.hover(function() {
    tip.toFront().animate({opacity: 1}, 500)
  },function() {
    tip.animate({opacity: 0}, 500).toBack()
  });
  return tip;
}

cbotGlobal.glowArrow=null;

function connectUsing(index,state,other,outNum,tipTxt) {
  var pt=[parseInt(state.r_t_i_set[0].attrs.x,10)+state.r_t_i_set[0].attrs.width/2,
      parseInt(state.r_t_i_set[0].attrs.y,10)+state.r_t_i_set[0].attrs.height/2,
      parseInt(other.r_t_i_set[0].attrs.x,10)+other.r_t_i_set[0].attrs.width/2,
      parseInt(other.r_t_i_set[0].attrs.y,10)+other.r_t_i_set[0].attrs.height/2]
  var i;
  for (i=0; i<pt.length; i++) {
    pt[i]=Math.floor(pt[i]);
  }
  var arr=cbotGlobal.workspace.arrow(pt[0],pt[1],pt[2],pt[3],4);
  arr[1].data("state",state);
  arr[1].data("other",other);
  arr[1].hover(function() {
    //state.glowArrow=arr[1].glow();
    removeGlobal("glowArrow");
    cbotGlobal.glowArrow=this.glow();
  }, function() {
    //state.glowArrow.remove();
    removeGlobal("glowArrow");
  });
  arr[1].click(function(evt) {
    var state=this.data("state");
    var other=this.data("other");
    var connectArr=cbotGlobal.conf.states[state.conf_idx].flow.connect;
    var i=connectArr.length-1;
    while (connectArr[i]!==other.key && i>=0) i-=2;
    if (i>=0) {
      if (i===(connectArr.length-1)) {
        alert("este es el camino default");
      }
      else {
        jQuery("#regexp").val(connectArr[i+1]);
        $("#arrowStateName").val(state.key);
        $("#arrowOtherStateName").val(other.key);
        $("#arrowConfIdx").val(state.conf_idx);
        $("#arrowCnctIdx").val(i+1);
        cbotGlobal.arrowDialog.dialog("open");
        //alert("rr="+$("#regexp"));
      }
      
    }
    else {
      alert("esto no puede pasar!");
    }
    
/*    jQuery("#arrowDialog").simpleDialog({
      showCloseLabel:false,
      open: function() {alert("open")},
      close:function() {alert("close")}
    });*/
  });
  var tip=labelIt((pt[0]+pt[2])/2,
                  (pt[1]+pt[3])/2,
                  outNum+" ["+tipTxt+"]",
                  state.r_t_i_set);
  if (index<0) {
    state.statesOut.push(other.key);
    state.arrowsOut.push(arr);
    state.tipsOut.push(tip);
    other.statesIn.push(state.key);
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
  var confStates=cbotGlobal.conf.states;
  for (i=0; i<confStates.length; i+=1) {
    state=cbotGlobal.states[confStates[i].key];
    connectArr=confStates[i].flow.connect;// i es === a state.conf_idx
    if (connectArr!==undefined) {
      for (j=0; j<connectArr.length; j+=2) {
        other=cbotGlobal.states[connectArr[j]];
        connect(state,other,Math.floor(j/2+1),
                connectArr[j+1]!==undefined?connectArr[j+1]:"default");
      }
    }
  }
  startMonitoring(true);
}

function buildWorkspace(conf) {
  var i;
  removeGlobal("connectImage");
  removeGlobal("connectingPath");
  cbotGlobal.conf=conf;
  //jQuery("#states").empty();
  cbotGlobal.workspace.clear();

  var rStart=cbotGlobal.workspace.image("/images/robot-start.gif",100,100,15,15).toFront().attr({opacity: 0});  
  var rStop=cbotGlobal.workspace.image("/images/robot-stop.gif",100,100,15,15).toFront().attr({opacity: 0});
  var rWaiting=cbotGlobal.workspace.image("/images/robot-waiting.gif",100,100,15,15).toFront().attr({opacity: 1});  
  var robot=cbotGlobal.workspace.set(rStart,rStop,rWaiting);
  cbotGlobal.robot=robot;


  cbotGlobal.states={};
  //cbotGlobal.idx2key=[];
  for (i=0; i<conf.states.length; i+=1) {
    buildState(i,conf.states[i]);
  }
  connectStates();
  //setTimeout(function() {connectStates()},30);
}

function startMonitoring(newUUID) {
  if (cbotGlobal.monitoring) {
    if (newUUID) cbotGlobal.lastUUID=localUUID();
    jQuery.ajax({
      url:"/apps/"+cbotGlobal.app+"/"+cbotGlobal.inst,
      dataType: "json",
      data: {cmd: "current-pos",
             uuid: cbotGlobal.lastUUID,
             timeout: "20000"},
      success: handler1});  
  }
}

function showRobot(idx) {
  var i;
  if (cbotGlobal.robot !== undefined) {
    for (i=0; i<3; i+=1) {
      cbotGlobal.robot[i].attr({opacity: 0});
    }
    if (idx>=0) {
      cbotGlobal.robot[idx].animate({opacity: 1},500);
    }  
  }
}

function editStateConf() {
  var state=null;
  if (this.group!==undefined) {
    state=cbotGlobal.states[this.group[0].key];
  }
  if (state) {
    jQuery("#state-create").hide();
    jQuery("#state-edit").show();
    var stateConf=cbotGlobal.conf.states[state.conf_idx];
    
    jQuery("#state-key").html(stateConf["key"]);
    jQuery("#state-x").html(stateConf["flow"]["x"]);
    jQuery("#state-y").html(stateConf["flow"]["y"]);
    var ttt=jQuery("#state-connect");
    ttt.html(stateConf["flow"]["connect"]+"");
    //jQuery("#state-connect").html(stateConf["flow"]["connect"]);
  }
}

function handler1(result) {
  if (cbotGlobal.lastUUID===result["request-uuid"]) {
    if (cbotGlobal.states.length===0) {
      alert("there ara no states to monitor!");
      return;
    }
    var newState=cbotGlobal.states[result.current];
    if (newState===undefined) {
      //alert("there is no state with name "+result.current+" !");
      startMonitoring(true);
      return;
    }
    var thumbUp=$("#thumb-up");
    var thumbDn=$("#thumb-down");
    cbotGlobal.lastUUID=result.uuid;
    thumbUp.hide();
    thumbDn.hide();  
    if (result.status === ":bad") {
      thumbDn.show();
    }
    else {
      thumbUp.show();
    }
    jQuery("#resume").hide();
    jQuery("#stop-button").hide();
    jQuery("#start-button").hide();

    if (cbotGlobal.lastState !== null) {  
      cbotGlobal.lastState.r_t_i_set[0].animate({"fill": "#bbbbbb", 
                                     "stroke-width": 3,
                                     "stroke": "#007eaf", 
                                     },1000);
    }
    cbotGlobal.lastState=newState;
    cbotGlobal.lastState.r_t_i_set[0].animate({fill: "#eeeeee", 
                                   stroke: "#c42530",
                                   "stroke-width": 3},1000);    

    var resultIndex=0;
    var x=jQuery("table.status").find("tr").each(function (i) {
      if (i>0) {
        $(this).find("td").each(function (j) {
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
      jQuery("#stop-button").show();
      if (result["awaiting?"]) {
        showRobot(2);
        jQuery("#resume").show();
      }
      else {
        showRobot(0);
      }
    }
    else {
      jQuery("#start-button").show();
      showRobot(1);
    }
    var xx=parseInt(cbotGlobal.states[result.current].r_t_i_set[0].attrs.x,10);
    var yy=parseInt(cbotGlobal.states[result.current].r_t_i_set[0].attrs.y,10);
    cbotGlobal.robot.animate({x: xx+30,y: yy},500);
    startMonitoring(false);
  }
}

cbotGlobal.oprOKFunc={};

cbotGlobal.oprOKFunc["sleep-opr"] = function () {
  var state=cbotGlobal.states[$("#sleep-oprStateName").val()];
  var delta=$("#sleep-oprDelta").val();
  cbotGlobal.conf.states[state.conf_idx]["conf-map"].conf={"delta":delta};
}
cbotGlobal.oprOKFunc["socket-opr"] = function () {
  var div=$("#socket-oprDialog");
  var state=cbotGlobal.states[div.find("#state-name").val()];
  var host=div.find("#host").val();
  var port=div.find("#port").val();
  var timeout=div.find("#timeout").val();
  var rCount=div.find("#retry-count").val();
  var rDelay=div.find("#retry-delay").val();
  cbotGlobal.conf.states[state.conf_idx]["conf-map"]["timeout"]=timeout;
  cbotGlobal.conf.states[state.conf_idx]["conf-map"]["retry-count"]=rCount;
  cbotGlobal.conf.states[state.conf_idx]["conf-map"]["retry-delay"]=rDelay;
  cbotGlobal.conf.states[state.conf_idx]["conf-map"].conf={"host":host,"port":port};
}

function getDialog4Opr(opr) {
  var oprDiag=opr+"Dialog";
  if (cbotGlobal.oprDiags[oprDiag]===undefined) {
    cbotGlobal.oprDiags[oprDiag]=$("#"+oprDiag).dialog({
          autoOpen:false,
          modal:true,
          buttons : {
            "Ok":function() {
              cbotGlobal.oprOKFunc[opr]();
              $(this).dialog("close");
            },
            "Cancel" : function() {
              $(this).dialog("close");
            }
          }
        });
  }
  return cbotGlobal.oprDiags[oprDiag];
}

////////////////////////////////////////////////////////////
jQuery(document).ready(function() {
  cbotGlobal.workspace=Raphael("states","600","400");
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
      cbotGlobal.conf.states.push(newState);
    }
  });
  jQuery("#save-states").click(function () {
    if (cbotGlobal.app!==cbotGlobal.NV) {
      saveStates();
    }
    else {
      alert("first you must select application !");
    }
  });
  jQuery("#start-monitor-button").click(function () {
    setMonitoring(true);
    startMonitoring(true);
  });
  jQuery("#stop-monitor-button").click(function () {
    setMonitoring(false);
    startMonitoring(true);
  });
  cbotGlobal.arrowDialog=$("#arrowDialog").dialog({
          autoOpen:false,
          modal:true,
          buttons : {
            "Ok":function() {
              var state=cbotGlobal.states[$("#arrowStateName").val()];
              var other=cbotGlobal.states[$("#arrowOtherStateName").val()];
              var connectArr=cbotGlobal.conf.states[state.conf_idx].flow.connect;
              var i=$("#arrowCnctIdx").val();
              connectArr[i]=$("#regexp").val();
              removeArrows(state,other.key);
              reConnectStates(state,other.key);    
              $(this).dialog("close");
            },
            "Cancel" : function() {
              $(this).dialog("close");
            }
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


