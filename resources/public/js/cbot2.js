"use strict";

// jsROBOT Viewer
// InterWare de Mexico, S.A. de C.V.
// Felipe Gerard

/*jslint 
    indent: 2, browser: true, sloppy: true, vars: true, cap: false, plusplus: true, maxerr: 50 
*/

/*global jQuery, $, Raphael, alert */

Raphael.fn.arrow  =  function (x1, y1, x2, y2, size) {
  var angle  =  Math.atan2(x1 - x2, y2 - y1);
  angle  =  (angle / (2 * Math.PI)) * 360;
  var cx = (x1 + x2) / 2;
  var cy = (y1 + y2) / 2;
  var arrowPath  =  this.path("M" + cx + " " + cy + " L" + (cx - size) + " " + (cy - size) + " L" + (cx - size) + " " + (cy + size) + " L" + cx + " " + cy ).attr("fill", "black").rotate((90 + angle), cx, cy).toBack();
  var linePath  =  this.path("M" + x1 + " " + y1 + " L" + x2 + " " + y2).toBack();
  return [linePath, arrowPath];
};

var cbot = (function() {
  var NV = "unknown";
  var lastUUID = NV;
  var app = NV;
  var inst = NV;
  var states = {};
  var monitoring = true;
  var HIDE_ROBOT = -1;
  var RUNNING_ROBOT = 0;
  var STOP_ROBOT = 1;
  var WAIT_ROBOT = 2;
  var lastState = null;
  var etiqueta = ["state", "result", "when", "delta-micro"];
  var robot = null;
  var dragging = false;
  var connecting = false;
  var connectImage = null;
  var connectImageBBOX = null;
  var connectingPath = null;
  var workspace = null;
  var conf = null;
  var offsetX = 0;
  var offsetY = 0;
  var otherState = null;
  var otherGlow = null;
  var glowArrow = null;
  var connectingState = null;
  var oprDiags = {};
  var arrowDialog = null;
  
  var each = function(set, fun) {
    var i;
    for (i = 0; i < set.length; i++) {
      fun(set[i]);
    }
  };

  var removeIt = function (elem) {
    if (elem!==null) {
      elem.remove();
    }
    return null;
  }; 

  var getWithDefault = function(mapa, path, defaultValue) {
    var i;
    var value = mapa;
    for (i = 0; i < path.length; i++) {
      value = value[path[i]];
      if (value === undefined) {
        return defaultValue;
      }
    }
    return value;
  };

  var connectInConf = function(state, other, regExp) {
    var idx = state.conf_idx;
    var connectArr = conf.states[idx].flow.connect;
    if (connectArr === undefined) {
      connectArr = [];
      conf.states[idx].flow.connect = connectArr;
    }
    if (connectArr.length === 0) {
      connectArr.unshift(other.key);
    } else {
      connectArr.unshift(regExp);
      connectArr.unshift(other.key);      
    }
  };

  var connectingStart = function(x, y, event) {
    if (connecting) {
      return;
    }
    connecting = true;
    var bbox = connectImageBBOX;
    offsetX = event.offsetX - (bbox.x + bbox.width);
    offsetY = event.offsetY - (bbox.y + bbox.height);
    connectingPath = workspace.path("M" + (bbox.x + bbox.width) + " " + (bbox.y + bbox.height)); // + "l0 0"
  };

  var contains = function(arr, elem) {
    var i;
    for (i = 0; i < arr.length; i++) {
      if (arr[i] === elem) {
        return i;
      }
    }
    return -1;
  };

  var removeArrows = function(state, otherKey) {
    var removed = [];
    var i = 0;
    var remover = function (elem) {
      elem.remove();
    };
    for (i = 0; i < state.statesOut.length; i++) {
      if (state.statesOut[i] === otherKey || otherKey === "*") {
        each(state.arrowsOut[i], remover);
        if (state.tipsOut[i] !== undefined) {
          state.tipsOut[i].remove();
        }
        removed.push([state.key, state.statesOut[i]]);
      }
    }
    return removed;
  };

var labelIt = function(x, y, txt, elem) {
    var condition = workspace.text(x, y, txt);
    var bbox = condition.getBBox();
    var rr = workspace.rect(bbox.x - 2, bbox.y - 2, bbox.width + 4, bbox.height + 4);
    rr.attr({fill: "#fff"});
    var tip = workspace.set(rr, condition);
    tip.attr({opacity: 0}).toBack();//toFront();    
    elem.hover(function() {
      tip.toFront().animate({opacity: 1}, 500);
    }, function() {
      tip.animate({opacity: 0}, 500).toBack();
    });
    return tip;
  };

  var connectUsing = function(index, state, other, outNum, tipTxt) {
    var pt = [parseInt(state.r_t_i_set[0].attrs.x, 10) + state.r_t_i_set[0].attrs.width / 2,
        parseInt(state.r_t_i_set[0].attrs.y, 10) + state.r_t_i_set[0].attrs.height / 2,
        parseInt(other.r_t_i_set[0].attrs.x, 10) + other.r_t_i_set[0].attrs.width / 2,
        parseInt(other.r_t_i_set[0].attrs.y, 10) + other.r_t_i_set[0].attrs.height / 2];
    var i;
    for (i = 0; i < pt.length; i++) {
      pt[i] = Math.floor(pt[i]);
    }
    var arr = workspace.arrow(pt[0], pt[1], pt[2], pt[3], 4);
    arr[1].data("state", state);
    arr[1].data("other", other);
    arr[1].hover(function() {
      removeIt(glowArrow);
      glowArrow = this.glow();
    }, function() {
      glowArrow=removeIt(glowArrow);
    });
    arr[1].click(function() {
      var state = this.data("state");
      var other = this.data("other");
      var connectArr = conf.states[state.conf_idx].flow.connect;
      var i = connectArr.length - 1;
      while (connectArr[i] !== other.key && i >= 0) {
        i -= 2;
      }
      if (i >= 0) {
        if (i === (connectArr.length - 1)) {
          jQuery("#regexp").val("default (do not change)").attr({disabled: true});
        } else {
          jQuery("#regexp").val(connectArr[i + 1]).attr({disabled: false});
        }
        $("#arrowStateName").val(state.key);
        $("#arrowOtherStateName").val(other.key);
        $("#arrowCnctIdx").val(i + 1);
        arrowDialog.dialog("open");
      } else {
        alert("esto no puede pasar!");
      }
    });
    var tip = labelIt((pt[0] + pt[2]) / 2,
                    (pt[1] + pt[3]) / 2,
                    outNum + " [" + tipTxt + "]",
                    state.r_t_i_set);
    if (index < 0) {
      state.statesOut.push(other.key);
      state.arrowsOut.push(arr);
      state.tipsOut.push(tip);
      other.statesIn.push(state.key);
    } else {
      state.arrowsOut[index] = arr;
      state.tipsOut[index] = tip;
    }
  };

  var reConnectStates = function(state, otherName) {
    var i;
    for (i = 0; i < state.statesOut.length; i++) {
      var other = states[state.statesOut[i]];
      if (other.key === otherName || otherName === "*") {
        var tipTxt = conf.states[state.conf_idx].flow.connect[i * 2 + 1] !== undefined ?
              conf.states[state.conf_idx].flow.connect[i * 2 + 1] : "default";
        connectUsing(i, state, other, i + 1, tipTxt);
      }
    }
  };

  var connectingStop = function () {
    workspace.renderfix();
    connectingPath.remove();
    if (otherState !== null) {
      var state = connectingState;
      var other = otherState;
      if (contains(state.statesOut, other.key) < 0) {
        connectInConf(state, other, "undefined");
        removeArrows(state, "*");
        state.statesOut.unshift(other.key);
        reConnectStates(state, "*");
        other.statesIn.push(state.key);
      }
    }
    otherGlow=removeIt(otherGlow);
    connectingPath.remove();
    connectingPath = null;
    connectImage.remove();
    connectImage = null;
    connectImageBBOX = null;
    otherState = null;
    connecting = false;
  };

  var connectingMove = function(dx, dy) {
    var bbox = connectImageBBOX;
    connectingPath.attr({"path": "M" + (bbox.x + bbox.width) + " " + (bbox.y + bbox.height) + "l" + (dx + offsetX) + " " + (dy + offsetY)});
  };

  var mouseIsOver = function() {
    if (dragging) {
      return;
    }
    if (connectImage !== null) {
      if (connectingPath !== null) {
        if (connectImageBBOX !== this.group[0].getBBox()) { //ojo con el this
          // estamos conectando y ahora sobre OTRO estado
          otherState = states[this.group[0].key];
          otherGlow=removeIt(otherGlow);
          otherGlow = this.group[0].glow();
          return;
        } else {
          otherGlow=removeIt(otherGlow);
          otherState = null;
          return;
        }
      } else {
        connectImage=removeIt(connectImage);
      }
    }
    var bbox = this.group[0].getBBox();
    var p = workspace.path("M" + (bbox.x + bbox.width) + " " + (bbox.y + bbox.height) + "l -6 -2 -6 2 2 -6 -4 -2 4 -2 -2 -6 6 2 6 -2 -2 6 4 2 -4 2 z").attr({"fill": "#ff0000"});
    connectImage = p;
    connectImageBBOX = bbox;
    connectImage.drag(connectingMove, connectingStart, connectingStop);
    connectingState = states[this.group[0].key];
  };

  var mouseIsOut = function() {
    if (this.type === "rect") { //OJO con el this
      otherGlow=removeIt(otherGlow);
      otherState = null;
    }
  };

  var getCenter = function(stateName) {
    var state = states[stateName];
    var bbox = state.r_t_i_set[0].getBBox();
    var x = Math.floor(bbox.x + bbox.width / 2);
    var y = Math.floor(bbox.y + bbox.height / 2);
    return {"x": x, "y": y};
  };

  var createPathStr = function(from, to) {
    return "M" + from.x + ", " + from.y + "L" + to.x + ", " + to.y;
  };

  var connect = function(state, other, outNum, tipTxt) {
    connectUsing(-1, state, other, outNum, tipTxt);
  };

  var localUUID = function() {
    return Raphael.createUUID();
  };

 var showRobot = function(idx) {
    var i;
    if (robot !==  null) {
      for (i = 0; i < 3; i += 1) {
        robot[i].attr({opacity: 0});
      }
      if (idx >= 0) {
        robot[idx].animate({opacity: 1}, 500);
      }
    }
  };

  var startMonitoring = function(newUUID) {
    if (monitoring) {
      if (newUUID) {
        lastUUID = localUUID();
      }
      jQuery.ajax({
        url: "/apps/" + app + "/" + inst,
        dataType: "json",
        data: {
          cmd: "current-pos",
          uuid: lastUUID,
          timeout: "20000",
          json: "true"
        },
        success: function(result) {
          if (lastUUID === result["request-uuid"]) {
            if (states.length === 0) {
              alert("there ara no states to monitor!");
              return;
            }
            var newState = states[result.current];
            if (newState === undefined) {
              alert("there is no state with name " + result.current + " !");
              startMonitoring(true);
              return;
            }
            var thumbUp = $("#thumb-up");
            var thumbDn = $("#thumb-down");
            lastUUID = result.uuid;
            thumbUp.hide();
            thumbDn.hide();
            if (result.status === ":bad") {
              thumbDn.show();
            } else {
              thumbUp.show();
            }
            jQuery("#resume").hide();
            jQuery("#stop-button").hide();
            jQuery("#start-button").hide();

            if (lastState !==  null) {
              lastState.r_t_i_set[0].animate({"fill": "#bbbbbb",
                                              "stroke-width": 3,
                                              "stroke": "#007eaf"
                                             }, 1000);
            }
            lastState = newState;
            lastState.r_t_i_set[0].animate({fill: "#eeeeee",
                                            stroke: "#c42530",
                                            "stroke-width": 3}, 1000);

            var resultIndex = 0;
            jQuery("table.status").find("tr").each(function (i) {
              if (i > 0) {
                $(this).find("td").each(function (j) {
                  if (resultIndex < result.stats.info.length) {
                    $(this).text(result.stats.info[resultIndex][etiqueta[j]]);
                  } else {
                    $(this).text("");
                  }
                });
                resultIndex += 1;
              }
            });
            if (!result["stop?"]) {
              jQuery("#stop-button").show();
              if (result["awaiting?"]) {
                showRobot(WAIT_ROBOT);
                jQuery("#resume").show();
              } else {
                showRobot(RUNNING_ROBOT);
              }
            } else {
              jQuery("#start-button").show();
              showRobot(STOP_ROBOT);
            }
            var xx = parseInt(states[result.current].r_t_i_set[0].attrs.x, 10);
            var yy = parseInt(states[result.current].r_t_i_set[0].attrs.y, 10);
            robot.animate({x: xx + 30, y: yy}, 500);
            startMonitoring(false);
          }
        }        
      });
    }
  };

  var connectStates = function() {
    var i, j;
    var state, other;
    var connectArr;
    var confStates = conf.states;
    for (i = 0; i < confStates.length; i += 1) {
      state = states[confStates[i].key];
      connectArr = confStates[i].flow.connect;// i es  ===  a state.conf_idx
      if (connectArr !== undefined) {
        for (j = 0; j < connectArr.length; j += 2) {
          other = states[connectArr[j]];
          connect(state, other, Math.floor(j / 2 + 1),
                  connectArr[j + 1] !== undefined ? connectArr[j + 1] : "default");
        }
      }
    }
    startMonitoring(true);
  };

  var dragStateStart = function() {
    if (dragging || connecting) {
      return;
    }
    var state = null;
    if (this.group !== undefined) {//OJO con this
      dragging = true;
      state = states[this.group[0].key];
    }
    if (state) {
      connectImage=removeIt(connectImage);
      connectingPath=removeIt(connectingPath);
      var i;
      for (i = 0; i < state.r_t_i_set.length; i++) {
        state.r_t_i_set[i].ox = parseInt(state.r_t_i_set[i].attrs.x, 10);
        state.r_t_i_set[i].oy = parseInt(state.r_t_i_set[i].attrs.y, 10);
      }
      //desconectar elemento
      var arrowsOut = removeArrows(state, "*");
      var arrowsIn = [];
      each(state.statesIn, function (name) {
        var other = states[name];
        var arrows = removeArrows(other, state.key);
        each(arrows, function(par) {arrowsIn.push(par); });
      });
      var pathsOut = [];
      each(arrowsOut, function(par) {
        var from = getCenter(par[0]); from.x0 = from.x; from.y0 = from.y;
        var to = getCenter(par[1]); to.x0 = to.x; to.y0 = to.y;
        pathsOut.push({"from": from, 
                       "to": to, 
                       "path": workspace.path(createPathStr(from, to)).toBack().attr({"stroke": "#c42530"})});
      });
      var pathsIn = [];
      each(arrowsIn, function(par) {
        var from = getCenter(par[0]); from.x0 = from.x; from.y0 = from.y;
        var to = getCenter(par[1]); to.x0 = to.x; to.y0 = to.y;
        pathsIn.push({"from": from, 
                      "to": to, 
                      "path": workspace.path(createPathStr(from, to)).toBack().attr({"stroke": "#007eaf"})});
      });
      state.pathsOut = pathsOut;
      state.pathsIn = pathsIn;
    }
  };

  var dragStateStop = function() {
    if (!dragging || connecting) {
      return;
    }
    var state = null;
    if (this.group !== undefined) {//OJO con el this !!!
      state = states[this.group[0].key];
    }
    if (state) {
      var i;
      for (i = 0; i < state.r_t_i_set.length; i++) {
        delete (state.r_t_i_set[i].ox);
        delete (state.r_t_i_set[i].oy);
      }
      conf.states[state.conf_idx].flow.x = state.r_t_i_set[0].attrs.x;
      conf.states[state.conf_idx].flow.y = state.r_t_i_set[0].attrs.y;
      each(state.pathsIn, function(info) {info.path.remove(); });
      each(state.pathsOut, function(info) {info.path.remove(); });
      reConnectStates(state, "*");
      each(state.statesIn, function(name) {
      var other = states[name];
        reConnectStates(other, state.key);
      });
      dragging = false;
    }
  };
  
  var dragStateMove = function(dx, dy) {
    if (!dragging || connecting) {
      return;
    }
    var state = null;
    if (this.group !== undefined) {//OJO this
      state = states[this.group[0].key];
    }
    if (state) {
      var x;
      for (x = 0; x < state.r_t_i_set.length; x++) {
        var obj = state.r_t_i_set[x];
        obj.attr({ x: obj.ox + dx, y: obj.oy + dy });
      }
      each(state.pathsOut, function(info) {
        info.from.x = info.from.x0 + dx;
        info.from.y = info.from.y0 + dy;
        info.path.attr({"path": createPathStr(info.from, info.to)});
      });
      each(state.pathsIn, function(info) {
        info.to.x = info.to.x0 + dx;
        info.to.y = info.to.y0 + dy;
        info.path.attr({"path": createPathStr(info.from, info.to)});
      });
    }
  };

  var buildState = function(index, state) {
    var txt = workspace.text(75, 50, state.key);
    var icono = workspace.image("/images/" + state["conf-map"].opr + ".gif", 40, 50, 15, 15);
    var g = workspace.set(txt, icono);
    var bbox = g.getBBox();
    var rect = workspace.rect(bbox.x - 8, bbox.y - 2, bbox.width + 16, bbox.height + 4, 8);
    rect.attr({"stroke": "#007eaf",
               "stroke-width": 3,
               "fill": "#bbbbbb"}).toBack();
    g = workspace.set(rect, txt, icono);//.data("dim", rect.getBBox());
    g.attr({x: parseInt(state.flow.x, 10), y: parseInt(state.flow.y, 10)});
    g[1].attr({x: parseInt(g[1].attrs.x, 10) + 15 + bbox.width / 2,
               y: parseInt(g[1].attrs.y, 10) + 18}).toFront();
    g[2].attr({x: parseInt(g[2].attrs.x, 10) + 4,
               y: parseInt(g[2].attrs.y, 10) + 7}).toFront();
    rect.key = state.key;
    g[0].group = g;
    g[2].group = g;
    g[1].group = g;
    states[state.key] = {"key": state.key,
                         "r_t_i_set": g,
                         "conf_idx": index,
                         "statesOut": [],
                         "arrowsOut": [],
                         "tipsOut": [],
                         "statesIn": []//, "glowArrow":null
                        };//statesIn los nombres
                        //de los estados que salen hacia ACA
    g.drag(dragStateMove, dragStateStart, dragStateStop);
    g.mouseover(mouseIsOver);
    g.mouseout(mouseIsOut);
    //g.dblclick(updateState);
    return states[state.key];
  };

  var buildWorkspace = function(confParam) {
    var i;
    connectImage=removeIt(connectImage);
    connectingPath=removeIt(connectingPath);
    conf = confParam;
    workspace.clear();

    var rStart = workspace.image("/images/robot-start.gif", 100, 100, 15, 15).toFront().attr({opacity: 0});
    var rStop = workspace.image("/images/robot-stop.gif", 100, 100, 15, 15).toFront().attr({opacity: 0});
    var rWaiting = workspace.image("/images/robot-waiting.gif", 100, 100, 15, 15).toFront().attr({opacity: 1});
    robot = workspace.set(rStart, rStop, rWaiting);
    states = {};
    for (i = 0; i < conf.states.length; i += 1) {
      buildState(i, conf.states[i]);
    }
    connectStates();
  };

  var setTimeoutOpr = function(confState, div) {
    div.find("#timeout").val(confState["conf-map"].timeout);
    div.find("#retry-delay").val(confState["conf-map"]["retry-delay"]);
    div.find("#retry-count").val(confState["conf-map"]["retry-count"]);
  };

  var oprSetFunc = {
    "sleep-opr": function (state, confState) {
      var div = $("#sleep-oprDialog");
      div.find("#state-name").val(state.key);
      div.find("#delta").val(getWithDefault(confState, ["conf-map", "conf", "delta"], "1000"));
    },
    "socket-opr": function(state, confState) {
      var div = $("#socket-oprDialog");
      div.find("#state-name").val(state.key);
      setTimeoutOpr(confState, div);
      div.find("#host").val(getWithDefault(confState, ["conf-map", "conf", "host"], "localhost"));
      div.find("#port").val(getWithDefault(confState, ["conf-map", "conf", "port"], "22"));
    },
    "human-opr": function(state, confState) {
      var div = $("#human-oprDialog");
      div.find("#state-name").val(state.key);
    },
    "switch-good-opr": function(state, confState) {
      var div = $("#switch-good-oprDialog");
      div.find("#state-name").val(state.key);
    },
    "switch-bad-opr": function(state, confState) {
      var div = $("#switch-bad-oprDialog");
      div.find("#state-name").val(state.key);
      var minutes2wait = getWithDefault(confState, ["conf-map", "conf", "minutes2wait"], "5");
      div.find("#minutes2wait").val(minutes2wait);
    },
    "log-opr": function(state, confState) {
      var div = $("#log-oprDialog");
      div.find("#state-name").val(state.key);
      div.find("#text").val(getWithDefault(confState, ["conf-map", "conf", "text"], ""));
      var lev = getWithDefault(confState, ["conf-map", "conf", "level"], ":debug");
      div.find("#log-levels").find("option").each(function() {
        this.selected = $(this).val() === lev;
      });
    },
    "print-msg-opr": function (state, confState) {
      var div = $("#print-msg-oprDialog");
      div.find("#state-name").val(state.key);
      div.find("#msg").val(getWithDefault(confState, ["conf-map", "conf", "msg"], ""));
    },
    "print-context-opr": function (state, confState) {
      var div = $("#print-context-oprDialog");
      div.find("#state-name").val(state.key);
      div.find("#filter-re").val(getWithDefault(confState, ["conf-map", "conf", "filter-re"], ".*"));
    },
    "get-http-opr": function (state, confState) {
      var div = $("#get-http-oprDialog");
      div.find("#state-name").val(state.key);
      div.find("#url").val(getWithDefault(confState, ["conf-map", "conf", "url"], "http://"));
    },
    "post-http-opr": function (state, confState) {
      var div = $("#post-http-oprDialog");
      div.find("#state-name").val(state.key);
      div.find("#url").val(getWithDefault(confState, ["conf-map", "conf", "url"], "http://"));
      div.find("#params").val(getWithDefault(confState, ["conf-map", "conf", "params"], "name = value\nname = value"));
    },
    "clojure-opr": function (state, confState) {
      var div = $("#clojure-oprDialog");
      div.find("#state-name").val(state.key);
      div.find("#code").val(getWithDefault(confState, ["conf-map", "conf", "code"], ""));
    }
  };

  var removeState = function(opr) {
    var div = $("#" + opr + "Dialog");
    var state = states[div.find("#state-name").val()];
    var i, j;
    var nstates = [];
    var connectVec;
    var cur;
    for (i = 0; i < conf.states.length; i++) {
      cur = conf.states[i];
      if (cur.key !== state.key) {
        connectVec = [];
        if (cur.flow.connect !== undefined) {
          for (j = 0; j < cur.flow.connect.length; j += 2) {
            if (cur.flow.connect[j] !==  state.key) {
              connectVec.push(cur.flow.connect[j]);
              if (j < cur.flow.connect.length - 1) {
                connectVec.push(cur.flow.connect[j + 1]);
              }
            }
          }
        }
        cur.flow.connect = connectVec;
        nstates.push(conf.states[i]);
      }
    }
    conf.states = nstates;
    buildWorkspace(conf);
  };

  var getTimeoutOpr = function(div, state) {
    var timeout = div.find("#timeout").val();
    var rCount = div.find("#retry-count").val();
    var rDelay = div.find("#retry-delay").val();
    conf.states[state.conf_idx]["conf-map"].timeout = timeout;
    conf.states[state.conf_idx]["conf-map"]["retry-count"] = rCount;
    conf.states[state.conf_idx]["conf-map"]["retry-delay"] = rDelay;
  };

  var oprOKFunc = {
    "sleep-opr": function () {
      var div = $("#sleep-oprDialog");
      var state = states[div.find("#state-name").val()];
      var delta = div.find("#delta").val();
      conf.states[state.conf_idx]["conf-map"].conf = {"delta": delta};
    },
    "socket-opr": function () {
      var div = $("#socket-oprDialog");
      var state = states[div.find("#state-name").val()];
      getTimeoutOpr(div, state);
      var host = div.find("#host").val();
      var port = div.find("#port").val();
      conf.states[state.conf_idx]["conf-map"].conf = {"host": host, "port": port};
    },
    "human-opr": function () {
    },
    "switch-good-opr": function () {
    },
    "switch-bad-opr": function () {
      var div = $("#switch-bad-oprDialog");
      var state = states[div.find("#state-name").val()];
      var minutes2wait = div.find("#minutes2wait").val();
      conf.states[state.conf_idx]["conf-map"].conf = {"minutes2wait": minutes2wait};
    },
    "log-opr": function () {
      var div = $("#log-oprDialog");
      var state = states[div.find("#state-name").val()];
      var level = div.find("#log-levels option:selected").val();
      var text = div.find("#text").val();
      conf.states[state.conf_idx]["conf-map"].conf = {"level": level, "text": text};
    },
    "print-msg-opr": function () {
      var div = $("#print-msg-oprDialog");
      var state = states[div.find("#state-name").val()];
      var msg = div.find("#msg").val();
      conf.states[state.conf_idx]["conf-map"].conf = {"msg": msg};
    },
    "print-context-opr": function () {
      var div = $("#print-context-oprDialog");
      var state = states[div.find("#state-name").val()];
      var filter = div.find("#filter-re").val();
      conf.states[state.conf_idx]["conf-map"].conf = {"filter-re": filter};
    },
    "get-http-opr": function () {
      var div = $("#get-http-oprDialog");
      var state = states[div.find("#state-name").val()];
      getTimeoutOpr(div, state);
      var url = div.find("#url").val();
      conf.states[state.conf_idx]["conf-map"].conf = {"url": url};
    },
    "post-http-opr": function () {
      var div = $("#post-http-oprDialog");
      var state = states[div.find("#state-name").val()];
      getTimeoutOpr(div, state);
      var url = div.find("#url").val();
      var params = div.find("#params").val();
      conf.states[state.conf_idx]["conf-map"].conf = {"url": url, "params": params};
    },
    "clojure-opr": function () {
      var div = $("#clojure-oprDialog");
      var state = states[div.find("#state-name").val()];
      var code = div.find("#code").val();
      conf.states[state.conf_idx]["conf-map"].conf = {"code": code};
    }
  };

  var updateState = function() {
    var state = states[this.group[0].key];
    var confState = conf.states[state.conf_idx];
    var opr = confState["conf-map"].opr;
    oprSetFunc[opr](state, confState);
    var getDialog4Opr = function(opr) {
      var oprDiag = opr + "Dialog";
      if (oprDiags[oprDiag] === undefined) {
        oprDiags[oprDiag] = $("#" + oprDiag).dialog({
          autoOpen: false,
          modal: true,
          buttons: {
            "Ok": function() {
              oprOKFunc[opr]();
              $(this).dialog("close");
            },
            "Remove": function() {
              removeState(opr);
              each(states,function (state) {
                state.r_t_i_set.dblclick(updateState);
              });
              $(this).dialog("close");
            },
            "Cancel": function() {
              $(this).dialog("close");
            }
          }
        });
      }
      return oprDiags[oprDiag];
    };
    getDialog4Opr(opr).dialog("open");
  };

  var buildWorkspaceAndInitDblClick = function(conf) {
    buildWorkspace(conf);
    var stateName;
    for (stateName in states) {
      if (states.hasOwnProperty(stateName)) {
        states[stateName].r_t_i_set.dblclick(updateState);
      }
    }
  };

  var fillSelect = function(json, tagLabel) {
    var combo = jQuery(tagLabel).empty();
    var selected = NV;
    var i;
    for (i = 0; i < json.length; i += 1) {
      if (i === 0) {
        combo.prepend('<option value = "' + json[i] + '" selected = "selected">' + json[i] + '</option>');
        selected = json[i];
      } else {
        combo.prepend('<option value = "' + json[i] + '">' + json[i] + '</option>');
      }
    }
    return selected;
  };

  var removeFromArr = function(arr, value) {
    var tmp = [];
    var i;
    for (i = 0; i < arr.length; i++) {
      if (value !== arr[i]) {
        tmp.push(arr[i]);
      }
    }
    return tmp;
  };

  var instanceChange = function() {
    showRobot(HIDE_ROBOT);
    inst = jQuery("#instances option:selected").text();
    startMonitoring(true);
  };

 var fillInstances = function(instances) {
    inst = fillSelect(instances, "#instances");
    jQuery("#instances").change(instanceChange);
  };

  var applicationChange = function() {
    showRobot(HIDE_ROBOT);
    app = jQuery("#applications option:selected").text();
    inst = NV;
    jQuery.ajax({
      url: "/apps/" + app,
      dataType: "json",        
      success: fillInstances
    });
    jQuery.ajax({
      url: "/conf/" + app,
      dataType: "json",
      success: buildWorkspaceAndInitDblClick
    });
  };

  var setMonitor = function(monitorVal) {
    monitoring = monitorVal;
    if (!monitoring) {
      showRobot(HIDE_ROBOT);
    }
  };

  return {
    fillApplications: function(apps) {
      if (apps.length > 0) {
        app=fillSelect(apps,"#applications");
        jQuery("#applications").change(applicationChange);
        jQuery.ajax({
          url: "/apps/" + app,
          dataType: "json",
          success: this.fillInstances
        });
        inst=NV; 
        jQuery.ajax({
          url: "/conf/" + app,
          dataType: "json",
          success: buildWorkspaceAndInitDblClick
        });
      }
    },
    fillOperations: function(oprs) {
      fillSelect(oprs, "#operations");
    },
    setMonitoring: function(monitoringVal) {
      setMonitor(monitoringVal);
    },
    startInstance: function() {
      jQuery.ajax({
        url: "/apps/" + app + "/" + inst,
        //dataType: "json",
        data: {"cmd": "start"},
        success: function(result) {
          jQuery("#result-str").text(result);
          startMonitoring(false);
        }
      });
      jQuery("#start-button").hide();
    },
    stopInstance: function() {
      jQuery.ajax({
        url: "/apps/" + app + "/" + inst,
        //dataType: "json",
        data: {"cmd": "stop"},
        success: function(result) {
          jQuery("#result-str").text(result);
          startMonitoring(false);
        }
      });
      jQuery("#stop-button").hide();
    },
    resumeInstance: function() {
      var msg = jQuery("#resume-msg").val();
      jQuery.ajax({
        url: "/apps/" + app + "/" + inst,
        //dataType: "json",
        data: {"cmd": "resume",
               "msg": msg},
        success: function(result) {
          jQuery("#result-str").text(result);
          startMonitoring(false);
        }
      });
      jQuery("#resume").hide();
    },
    saveStates: function() {
      jQuery.ajax({
        type: "POST",
        url: "/store/" + app,
        dataType: "json",
        data: {"conf": conf},
        success: function(result) {
          alert(result.result);
          applicationChange();
          setMonitor(true);
          startMonitoring(true);
        }
      });
    },
    setWorkspace: function (wrkspc) {
      workspace=wrkspc;
    },
    currentApp: function() {
      return app;
    },
    gNV: function() {
      return NV;
    },
    addNewState: function(newState) {
      var len=0;
      if (conf !== undefined && conf.states !== undefined) {
        len=conf.states.length;
      }
      buildState(len,newState).r_t_i_set.dblclick(updateState);
      conf.states.push(newState);
    },
    createArrowDiag: function() {
      arrowDialog = $("#arrowDialog").dialog({
        autoOpen: false,
        modal: true,
        buttons : {
          "Ok": function() {
            var state = states[$("#arrowStateName").val()];
            var other = states[$("#arrowOtherStateName").val()];
            var connectArr = conf.states[state.conf_idx].flow.connect;
            var i = $("#arrowCnctIdx").val();
            connectArr[i] = $("#regexp").val();
            removeArrows(state, other.key);
            reConnectStates(state, other.key);
            $(this).dialog("close");
          },
          "Remove": function() {
            var state = states[$("#arrowStateName").val()];
            var other = states[$("#arrowOtherStateName").val()];
            var connectArr = conf.states[state.conf_idx].flow.connect;
            var i = parseInt($("#arrowCnctIdx").val(), 10) - 1;
            var nconnectArr = [];
            var j;
            for (j = 0; j < connectArr.length; j += 2) {
              if (i !== j) {
                nconnectArr.push(connectArr[j]);
                if (j < connectArr.length - 1 && i !== connectArr.length - 1) {
                  nconnectArr.push(connectArr[j + 1]);
                }
              }
            }
            removeArrows(state, other.key);
            state.statesOut = removeFromArr(state.statesOut, other.key);
            other.statesIn = removeFromArr(other.statesIn, state.key);
            conf.states[state.conf_idx].flow.connect = nconnectArr;
            $(this).dialog("close");
          },
          "Cancel": function() {
            $(this).dialog("close");
          }
        }
      });
    } 
  };
}());


jQuery(document).ready(function() {
  cbot.setWorkspace(new Raphael("states", "600", "400"));
  cbot.createArrowDiag();
  jQuery("#start-button").click(cbot.startInstance);
  jQuery("#stop-button").click(cbot.stopInstance);
  jQuery("#resume-button").click(cbot.resumeInstance);
  jQuery("#add-state").click(function() {
    if (cbot.currentApp() !== cbot.gNV()) {
      var stateName = ":" + jQuery("#state-name").val();
      var stateOpr = jQuery("#operations option:selected").text();
      var newState = {flow: {x: 500, y: 300, connect: []}, key: stateName,
                    "conf-map": {opr: stateOpr, conf: []}};
      cbot.addNewState(newState);
    }
  });
  jQuery("#save-states").click(cbot.saveStates);

  jQuery("#start-monitor-button").click(function () {
    cbot.setMonitoring(true);
    cbot.startMonitoring(true);
  });  
  jQuery("#stop-monitor-button").click(function () {
    cbot.setMonitoring(false);
    cbot.startMonitoring(true);
  });
  jQuery.ajax({
    url: "/operations",
    dataType: "json",
    success: cbot.fillOperations
  });

  var apps = jQuery("#applications");
  apps.empty();
  jQuery.ajax({
    url: "/apps",
    dataType: "json",
    success: cbot.fillApplications
  });
});

