window.onload = function() {  
/*
  var paper = new Raphael(document.getElementById('canvas_container'), 500, 500);
  var circ = paper.circle(250, 250, 40);  
  circ.attr({fill: '#000', stroke: 'none'}); 
  var text = paper.text(250, 250, 'Bye Bye Circle!')  
  text.attr({opacity: 0, 'font-size': 12}).toBack(); 
  circ.node.onmouseover = function() {  
    this.style.cursor = 'pointer';  
  } 
  circ.node.onclick = function() {  
    text.animate({opacity: 1}, 2000);  
    circ.animate({opacity: 0}, 2000, function() {  
        this.remove();  
    });  
  }  
  var figura = paper.path("M68,68h90v19h-90hz");
  var icono=paper.image("/images/switch-good-opr.gif",70,70,15,15);
  var eti=paper.text(120,77,"Se puso bien");
  eti.attr({"font-size": 12,id: "textito"}).toBack();
  eti.animate({x: 200, y:100},4000);
  setTimeout(informa,6000);
  */

  
  //redraw();

    

}

function informa() {
  alert(jQuery("circle").x+","+jQuery("circle").y); 
}

var workspace;
var groups=[];

var states={};
var globales={};
globales.lastUUID="unknown";

globales.pstoke="#00f";
globales.pfill="eef";
globales.estroke="#1f1";
globales.efill="#afa";

globales.lastState=null;



function redraw() {
  workspace=Raphael("workspace","100%","100%");

  var txt=workspace.text(75,50,"El tecto\na mover");
  var icono=workspace.image("/images/switch-good-opr.gif",40,50,15,15);
  var g=workspace.set(txt,icono);
  var bbox=g.getBBox();
  var rect=workspace.rect(bbox.x-4,bbox.y-4,bbox.width+8,bbox.height+8,2);
  rect.attr({"stroke": "#000",
             "stroke-width": 8,
             "fill": "#000"}).toBack();  
  g=workspace.set(rect,txt,icono);//.data("dim",rect.getBBox());
  rect.idx=groups.length;
  g.bbox=g.getBBox();
  groups.push(g);
  g.drag(dragMove,dragStart,dragStop);

/*
  for (var i = 0; i < 5; i++) {
    workspace.circle(10 + 15 * i, 10, 10)
         .attr({fill: "#000"})
         .data("i", i)
         .click(function () {
            alert(this.data("i"));
         });
  }
  */
  var lin=workspace.path("M300,300,l 50 100");
  /*
  setTimeout(function() {
    workspace.clear(lin);
  },5000);
  */
}

function dragStart() {
  var g=null;
  if (!isNaN(this.idx)) {
    g=groups[this.idx];
  }
  if (g) {
    var i;
    for (i=0; i<g.items.length;i++) {
      g[i].ox=g[i].attr("x");
      g[i].oy=g[i].attr("y");
    }
  }
}

function dragStop() {
  var g=null;
  if (!isNaN(this.idx)) {
    g=groups[this.idx];
  }
  if (g) {
    var i;
    for (i=0; i<g.items.length;i++) {
      delete(g[i].ox);
      delete(g[i].oy);
    }
  }
}

var xx={};


function dragMove(dx,dy) {
  var g=null;
  if (!isNaN(this.idx)) {
    g=groups[this.idx];
  }
  if (g) {
    var x;
    for (x=0; x<g.items.length; x++) {
      var obj=g[x];
      //obj.attr({ x: obj.ox + dx, y: obj.oy + dy });
    }
    //alert(g.bbox.x+','+g.bbox.y+'  '+g.bbox.width+','+g.bbox.height);
    if (xx.comp) {
      xx.comp.remove();
    }
    xx.comp=workspace.path("M"+(g[0].ox+g.bbox.width/2)+","+(g[0].oy+g.bbox.height/2)+"l"+dx+","+dy).toBack();
  }
}


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

jQuery(document).ready (function () {
  jQuery.ajax({
    url: "/conf/WEB",
    success: buildWorkspace
  });
});


function buildState(state) {
  var txt=workspace.text(75,50,state.key);
  var icono=workspace.image("/images/"+state["conf-map"].opr+".gif",40,50,15,15);
  var g=workspace.set(txt,icono);
  var bbox=g.getBBox();
  var rect=workspace.rect(bbox.x-8,bbox.y-2,bbox.width+16,bbox.height+4,8);
  rect.attr({"stroke": "#007eaf",
             "stroke-width": 3,
             "fill": "#bbbbbb"}).toBack();  
  g=workspace.set(rect,txt,icono);//.data("dim",rect.getBBox());
  g.key=state.key;
  g.bbox=g.getBBox();
  states[g.key]=[g,state];
  g.animate({x:state.flow.x, y:state.flow.y},2000,"bounce",function () {
    this[1].animate({x: this[1].attrs.x+15+bbox.width/2,y: this[1].attrs.y+18},1000,"bounce").toFront();
    this[2].animate({x: this[2].attrs.x+4,y: this[2].attrs.y+8},1000,"bounce").toFront();
  });
}

function labelIt(x,y,txt,elem) {
  var condition=workspace.text(x,y,txt);
  var bbox=condition.getBBox();
  var rr=workspace.rect(bbox.x-2,bbox.y-2,bbox.width+4,bbox.height+4);
  rr.attr({fill: "#fff"});
  var tip=workspace.set(rr,condition);
  tip.attr({opacity: 0});    
  elem.hover(function() {tip.animate({opacity: 1}, 500); tip.toFront();},
             function() {tip.animate({opacity: 0}, 500)});
}

function connectIt(conf,i) {
  var i,j;
  var state,other;
  var connectArr;
  if (i<conf.states.length) {
    state=states[conf.states[i].key];
    connectArr=state[1].flow.connect;
    for (j=0; j<connectArr.length; j+=2) {
      other=states[connectArr[j]];
      var pt=[state[1].flow.x+state[0].bbox.width/2,
                  state[1].flow.y+state[0].bbox.height/2,
                  other[1].flow.x+other[0].bbox.width/2,
                  other[1].flow.y+other[0].bbox.height/2]
      var arr=workspace.arrow(pt[0],pt[1],pt[2],pt[3],4);
      var eti=Math.floor((j+1)/2+1)+") ";
      labelIt((pt[0]+pt[2])/2,
              (pt[1]+pt[3])/2,
              connectArr[j+1]!==undefined?eti+connectArr[j+1]:eti+"default",state[0]);
    }
    setTimeout(function() {connectIt(conf,i+1);},10);
  }
  else {
    var rStart=workspace.image("/images/robot-start.gif",40,50,15,15).toFront().attr({opacity: 1});  
    var rStop=workspace.image("/images/robot-stop.gif",40,50,15,15).toFront().attr({opacity: 0});
    var rWaiting=workspace.image("/images/robot-waiting.gif",40,50,15,15).toFront().attr({opacity: 0});  
    var robot=workspace.set(rStart,rStop,rWaiting);
    globales.robot=robot;
    startMonitoring();
  }
}

function connectStates(conf) {
  setTimeout(function() {connectIt(conf,0),1});
}

function buildWorkspace(confStr) {
  var i;
  var conf=eval("("+confStr+")");
  workspace=Raphael("workspace","100%","100%");
  for (i=0; i<conf.states.length; i+=1) {
    buildState(conf.states[i]);
  }
  setTimeout(function() {connectStates(conf)},3000);
}

function startMonitoring() {
  jQuery.ajax({
    url:"/apps/WEB/primaria",
    dataType: "json",
    data: {cmd: "current-pos",
           uuid: globales.lastUUID,
           timeout: "20000"},
    success: handler1});
}

function showRobot(idx) {
  var i;
  if (globales.robot[idx].attrs.opacity!==1) {
    for (i=0; i<3; i+=1) {
      globales.robot[i].attr({opacity: 0});
    }
    globales.robot[idx].animate({opacity: 1},500);
  }
}

function handler1(result) {
  if (globales.lastState !== null) {  
    globales.lastState[0].animate({"fill": "#bbbbbb", 
                                   "stroke-width": 3,
                                   "stroke": "#007eaf", 
                                   },1000);
  }
  globales.lastState=states[result.current][0];

  globales.lastState[0].animate({fill: "#eeeeee", 
                                 stroke: "#c42530",
                                 "stroke-width": 3},1000);
                                 
  if (true) {
    globales.lastUUID=result.uuid;
    var xx=parseInt(result.x,10);
    var yy=parseInt(result.y,10);  
    globales.robot.animate({x: xx+30,y: yy},500);
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
    startMonitoring();
  }
}


