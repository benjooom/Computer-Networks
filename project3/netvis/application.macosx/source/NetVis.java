import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import java.util.HashSet; 
import controlP5.*; 
import json.*; 
import java.net.*; 
import java.lang.reflect.*; 
import java.io.InputStreamReader; 
import java.math.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class NetVis extends PApplet {


/*

a - set A marker
b - set B marker
p - ping from A to B
e - toggle edge between A and B

*/

Graph g;

//ControlWindow adjustWindow;
ControlGroup adjustWindow;

Slider zoomSlider;
Slider repulsionSlider;
Slider springSlider;
Slider centerSlider;

class App extends JSONConnection
{
  ArrayList<Packet> packets = new ArrayList<Packet>();
  Node dragged;
  Node hovered;
  PApplet applet;
  
  App (PApplet applet)
  {
    size(600, 600);
    this.applet = applet;
    frame.setResizable(true);
    //g = buildRandomGraph(30, 25);
    g = new Graph();
    
    frameRate(20);
    smooth();
    
    CColor col = new CColor();
    col.setForeground(0xff8050f0);
    col.setBackground(0xff4020a0);
    col.setActive(0xff9070ff);
    
    control = new ControlP5(applet);
    //adjustWindow = control.addControlWindow("Adjustments", 230, 130);
    //adjustWindow.setBackground(0x7f6040a0);
    //adjustWindow.hideCoordinates();
    //adjustWindow.hide();
    adjustWindow = control.addGroup("Layout", 230, 130);
    adjustWindow.setPosition(0,10);
    adjustWindow.close();
    adjustWindow.setBackgroundColor(0xff6040a0);
    adjustWindow.setBackgroundHeight(130);
    adjustWindow.setWidth(230);
    
    zoomSlider = control.addSlider("Zoom", -2, 1);
    zoomSlider.linebreak();
    zoomSlider.setValue(0);
    zoomSlider.setColor(col);
//zoomSlider.setWindow(adjustWindow);
zoomSlider.setGroup(adjustWindow);
    repulsionSlider = control.addSlider("Repulsion", .5f, 3);
    repulsionSlider.setValue(2);
    repulsionSlider.linebreak();
    repulsionSlider.setColor(col);
repulsionSlider.setGroup(adjustWindow);    

    springSlider = control.addSlider("Spring", .005f, 0.85f);
    springSlider.setValue(0.05f);
    springSlider.linebreak();
    springSlider.setColor(col);
springSlider.setGroup(adjustWindow);    

    centerSlider = control.addSlider("Centerness", .005f, 0.3f);
    centerSlider.setValue(0.1f);
    centerSlider.linebreak();
    centerSlider.setColor(col);
centerSlider.setGroup(adjustWindow);    
    
    //connect();
    //server = new Server(applet, 4444);
    //new JSONConnection("localhost", 4444, true);
    connect("localhost", 4444, true);
  } 

  
  public synchronized void mousePressed (Vector2D pos)
  {
    boolean missed = true;
    dragged = null;
    for (Node n : g.nodes)
    {
      n.selected = false;
      if (n.click(pos))
      {
        missed = false;
        break;
      }
    }
  }

  public synchronized void keyPressed (int k)
  {
    if (k == 'a' || k == 'b')
    {
      for (Node n : g.nodes)
      {
        if (n.selected)
        {
          if (k == 'a') 
          {
            if (g.a == n)
              g.a = null;
            else
              g.a = n;
          }
          else
          {
            if (g.b == n)
              g.b = null;
            else
              g.b = n;
          }
          break;
        }
      }    
    }
    else if (k == 'e')
    {
      if (g.a != null && g.b != null && g.a != g.b)
      {
        if (g.a.isConnectedTo(g.b))
          send(new Object[][]{{"type","delEdge"}, {"node1",g.a.label}, {"node2",g.b.label}});
        else
          send(new Object[][]{{"type","addEdge"}, {"node1",g.a.label}, {"node2",g.b.label}});
      }
    }
    else if (k == 'p')
    {
      if (g.a != null && g.b != null && g.a != g.b)
      {
        send(new Object[][]{{"type","ping"}, {"node1",g.a.label}, {"node2",g.b.label}});
      }
    }
  }
  
  public synchronized void mouseReleased (Vector2D pos)
  {
    g.running = true;
    dragged = null;
  }
  
  public synchronized void mouseMoved (Vector2D pos)
  {
  }
  
  public synchronized void mouseDragged (Vector2D pos)
  {
    g.running = true;
    if (dragged != null)
    {
      dragged.pos = pos;
    }
  }
  
  private Node getNode (json.JSONObject j, String key)
  {
    try
    {
      String name = j.getString(key.toLowerCase(), null);
      if (name == null) return null;
      for (Node n : g.nodes)
      {
        if (n.label.equals(name)) return n;
      }
    }
    catch (Exception e)
    {
    }
    return null;
  }
  
  private Node getNode (json.JSONArray j, int index)
  {
    try
    {
      String name = j.getString(index);
      if (name == null) return null;
      for (Node n : g.nodes)
      {
        if (n.label.equals(name)) return n;
      }
    }
    catch (Exception e)
    {
    }
    return null;
  }
  
  private int getColor (json.JSONObject msg, String key, int def)
  {
    try
    {
      if (msg.has(key))
      {
        json.JSONArray col = msg.getJSONArray(key);
        if (col.length() == 3 || col.length() == 4)
        {
          int r, g, b, a;
          r = constrain((int)(col.getDouble(0) * 255), 0, 255);
          g = constrain((int)(col.getDouble(1) * 255), 0, 255);
          b = constrain((int)(col.getDouble(2) * 255), 0, 255);
          if (col.length() == 4)
            a = constrain((int)(col.getDouble(3) * 255), 0, 255);
          else
            a = 255;
          return (r << 16) | (g << 8) | (b << 0) | (a << 24);
        }
      }
    }
    catch (Exception e)
    {
    }
    return def;
  }
  
  public synchronized void process (json.JSONObject msg)
  {
    String type = msg.getString("type","");
    
    Node node = getNode(msg, "node");
    Node node1 = getNode(msg, "node1");
    Node node2 = getNode(msg, "node2");
    
    if (type.equals("addEntity"))
    {
      String kind = msg.getString("kind", "circle").toLowerCase();
      Node n;
      if (kind.equals("square"))
        n = new RoundedSquareNode();
      else if (kind.equals("triangle"))
        n = new TriangleNode();
      else
        n = new CircleNode();
      n.label = msg.getString("label", "");
      //node oldNode = null;
      for (Node oldn : g.nodes)
      {
        if (oldn.label.equals(n.label))
        {
          if (oldn.getClass() == n.getClass())
          {
            // Reuse;
            println("Reusing a node");
            n = null;
            break;
          }
          else
          {
            println("Removing a node");
            g.nodes.remove(oldn);
            break;
          }
        }
      }
      if (n != null) g.nodes.add(n);
      g.running = true;
    }
    else if (type.equals("delEntity"))
    {
      g.running = true;
      ArrayList<Edge> dead = new ArrayList<Edge>(node.edges);
      for (Edge e : dead)
      {
        e.remove();
      }
      g.nodes.remove(node);
      if (g.a == node) g.a = null;
      if (g.b == node) g.b = null;
    }
    else if (type.equals("link"))
    {
      if (node1 == null || node2 == null)
        println("Asked to add bad link: " + msg);
      else
        new Edge(node1, (int)msg.getDouble("node1_port"), node2, (int)msg.getDouble("node2_port"));
      g.running = true;
    }
    else if (type.equals("unlink"))
    {
      Edge e = g.findEdge(node1, node2);
      if (e != null)
        e.remove();
      else
        println("no edge for " + node1 + "<->" + node2);
      g.running = true;
    }
    else if (type.equals("packet"))
    {
      double t = 1000;
      boolean drop = msg.has("drop") ? msg.getBoolean("drop") : false;
      if (msg.has("duration")) t = msg.getDouble("duration");
      Packet p = new Packet(node1, node2, t, drop);
      p.strokeColor = getColor(msg, "stroke", 0xffFFffFF);
      p.fillColor = getColor(msg, "fill", 0);//0x7fffffff);
      app.packets.add(p);
    }
    else if (type.equals("initialize"))
    {
      g.running = true;
      g.nodes.clear();
      g.a = null;
      g.b = null;
      json.JSONObject entities = msg.getJSONObject("entities");
      for (String k : json.JSONObject.getNames(entities))
      {
        //JSONObject msg = entities.getJSONObject(k);
        //String kind = msg.getString("kind", "circle");
        String kind = entities.getString(k, "circle");
        Node n;
        if (kind.equals("square"))
          n = new RoundedSquareNode();
        else if (kind.equals("triangle"))
          n = new TriangleNode();
        else
          n = new CircleNode();
        g.nodes.add(n);

        //n.label = msg.getString("label", "");
        n.label = k;
      }
      println("....");
      json.JSONArray links = msg.getJSONArray("links");
      for (int i = 0; i < links.length(); i++)
      {
        json.JSONArray l = links.getJSONArray(i);
        node1 = getNode(l, 0);
        node2 = getNode(l, 2);
        int node1_port = (int)l.getDouble(1);
        int node2_port = (int)l.getDouble(3);
        new Edge(node1, node1_port, node2, node2_port);
      }
    }
    else if (type.equals("clear"))
    {
      g.nodes.clear();
      g.a = null;
      g.b = null;
    }
  }
  
  public synchronized void draw ()
  {
    //background(0x00201030);
    background(0x00302050);
    
    if (g != null) g.drawLinks();
    
    ArrayList<Packet> nextPackets = new ArrayList<Packet>();
    for (Packet p : packets)
    {
      if (p.draw()) nextPackets.add(p);
    }
    packets = nextPackets;
    
    if (g != null)
    {
      g.drawNodes();
      if (g.a != null) g.a.drawArrow();
      if (g.b != null) g.b.drawArrow();
    }
  }
}


class Edge
{
  // An edge is a connection between two Nodes that acts like a spring
  // governed by Hooke's Law.

  Node a, b; // The two ends
  int port_a, port_b;
  
  public boolean equals (Object other)
  {
    if ((other instanceof Edge) == false) return false;
    Edge o = (Edge)other;
    if (o.a == a && o.b == b && o.port_a == port_a && o.port_b == port_b)  return true;
    return o.a == b && o.b == a && o.port_a == port_b && o.port_b == port_a;
  }
  
  Edge (Node a, int port_a, Node b, int port_b)
  {
    this.a = a;
    this.b = b;
    this.port_a = port_a;
    this.port_b = port_b;
    
    
    if (!a.edges.contains(this)) a.edges.add(this);
    if (!b.edges.contains(this)) b.edges.add(this);
  }

  public String toString ()
  {
    return a + "." + port_a + " <-> " + b  + "." + port_b;
  }
  
  public void remove ()
  {
    a.edges.remove(this);
    b.edges.remove(this);
  }
 
  public Node getOtherEnd (Node self)
  {
    if (a == self)
      return b;
    return a;
  }

  public void draw ()
  {
    stroke(255,255,255,128);
    strokeWeight(5);
    a.pos.drawLineTo(b.pos);
  }
}


ControlP5 control;

double scaleFactor = 1;
double translationX;
double translationY;

App app;

PFont[] fonts = new PFont[3];

public void setup ()
{
  for (int i = 0; i < fonts.length; i++)
  {
    int pt = 12 * (int)pow(2,i);
    fonts[i] = loadFont("LucidaGrande-Bold-" + pt + ".vlw");
  }
  textFont(fonts[fonts.length-1]);

  app = new App(this);
}

public void draw ()
{
  double z = zoomSlider.value();
  double oz = z;
  if (z < 0)
    z = 1 / (-z + 1);
  else
    z += 1;
  //print(oz + " " + z + "\n");
  translate((float)(-width*z/2+width/2), (float)(-height*z/2+height/2));
  scaleFactor = z;
  translationX = -width*z/2+width/2;
  translationY = -height*z/2+height/2;
  scale((float)z);
  //print((width*z) + "\n");
  background(0);
  fill(255,255,255);
  //(new Vector2D(300,300)).drawCircle(30);

  if (scaleFactor < 0.75f)
    textFont(fonts[0]);
  else if (scaleFactor < 1.5f)
    textFont(fonts[1]);
  else
    textFont(fonts[2]);

  app.draw();
  resetMatrix();
}

public Vector2D getMousePos ()
{
  return new Vector2D(mouseX-translationX, mouseY-translationY).dividedBy(scaleFactor);
}

public void mousePressed ()
{
  app.mousePressed(getMousePos());
}

public void mouseMoved ()
{
  app.mouseMoved(getMousePos());
}

public void mouseReleased ()
{
  app.mouseReleased(getMousePos());
}

public void keyPressed ()
{
  app.keyPressed(key);
}

public void mouseDragged ()
{
  app.mouseDragged(getMousePos());
}

class Graph
{
  Node a, b;
  
  ArrayList<Node> nodes = new ArrayList<Node>();

  boolean running = true;

  public double iterate ()
  {
    // Iterates the position of elements in the graph.
    // Returns the total movement of this iteration (so that you can stop
    // iterating when the graph stabilizes).

    double totalForce = 0; // Used to track how much force is actual exterted

    // We spring all nodes to something *like* the center.  This is
    // actually a combination of the center of the window and the centeroid
    // of all the nodes' positions.
    Vector2D centeroid = new Vector2D();
    for (Node a : g.nodes) centeroid = centeroid.plus(a.pos);
    centeroid = centeroid.dividedBy(g.nodes.size());
    
    // Center of window
    Vector2D center = new Vector2D(width,height).times(0.5f); 
    
    // Something center-ish
    center = center.times(0.75f).plus(centeroid.times(0.25f));

    for (Node a : g.nodes)
    {
      Vector2D f = new Vector2D(0, 0);

      // All nodes are sprung to the center-ish
      {
        Vector2D x = a.pos.minus(center);
        double m = x.getMagnitude();
        //double l = (((CircleNode)a).getRadius()/2) * .5;
        double l = 10;
        m = (m - l) / m;

        x = x.times(m);
        if (m > 1) continue; 

        Vector2D F = x.times(centerSlider.value());
        f = f.minus(F);        
      }
      
      
      // Calculate spring forces along edges
      // Use modified Hooke's Law: F = -kx (where x is displacement)

      for (Edge e : a.edges)
      {
//        final double k = 0.05; // Spring constant
double k = springSlider.value();
        Vector2D x = a.pos.minus(e.getOtherEnd(a).pos);
        double m = x.getMagnitude();
        Node b = e.getOtherEnd(a);
        double l = a.getRadius()/2+b.getRadius()/2;
        l *= 2;
        m = (m - l) / m;

        x = x.times(m);
        //if (m > 1) continue;

        Vector2D F = x.times(-k);
        f = f.plus(F);
        
        //Vector2D x = a.pos.minus(e.getOtherEnd(a).pos);
        //double m = x.getMagnitude();
        //x = x.times((m - spring_length) / m); // Make this a ratio
        //Vector2D F = x.times(-k);
        //f = f.plus(F);
      }

      // Calculate Coulumb repulsion between all nodes
      // F = ke*((q1*q2)/r^2)
      //  where ke is the Coulomb constant, ms are the masses, r is distance between masses
      for (Node b : g.nodes)
      {
        Vector2D d = a.pos.minus(b.pos);
        double rSquared = d.getMagnitude();
        rSquared *= rSquared;

        if (rSquared > 0)
        {
          final double ke = 30;
          Vector2D F = d.times(ke).times(a.getCharge() * b.getCharge() / rSquared);
          
          double factor = repulsionSlider.value();
          
          //if (a.isConnectedTo(b))
          //  factor = 2;
          //else
          //  factor = 2;
          
          f = f.plus(F.times(factor));
        }
      }

      if (a != app.dragged && !a.pinned)// && !a.selected)
      {
        a.pos = a.pos.plus(f);
        totalForce += f.getMagnitude();
      }
    }
    return totalForce;
  }

  public Edge findEdge (Node a, Node b)
  {
    for (Node n : nodes)
    {
      if (n == a)
      {
        for (Edge e : n.edges)
        {
          if (e.getOtherEnd(n) == b) return e;
        }
      }
    }
    return null;
  }
  
  public void drawLinks ()
  {
    if (running || (random(12) != 0))
    {
      running = iterate() > .6f;//.25;
      //if (!running) println("Idle " + (int)random(1,100));
    }
//running = true;
    HashSet<Edge> drawn = new HashSet<Edge>();
    for (Node n : nodes)
    {
      for (Edge e : n.edges)
      {
        if (!drawn.contains(e))
        {
          e.draw();
          drawn.add(e);
        }
      }
    }
  }
  
  public void drawNodes ()
  {
    for (Node n : nodes) n.draw(this);
  }
}







class JSONConnection extends Connection
{
  public void send (Object[][] kvs)
  {
    HashMap<String, Object> dict = new HashMap<String, Object>();
    for (Object[] kv : kvs)
    {
      assert(kv.length == 2);
      String k = kv[0].toString();
      dict.put(k, kv[1]);
    }
    //println(dict);
    //println(new JSONObject(dict).toString());
    send(new json.JSONObject(dict).toString() + "\n");
  }

  public void process (String msg)
  {
    process(new json.JSONObject(msg));
  }
  public void process (json.JSONObject msg)
  {
    println("Msg: " + msg);
  }
  
  JSONConnection ()
  {
    super();
  }
  
  JSONConnection (String addr, int port, boolean reconnect)
  {
    super(addr, port, reconnect);
  }
}

class Connection implements Runnable
{
  Socket sock;
  boolean reconnect;
  String addr;
  int port;
  
  public void process (String msg)
  {
  }
  
  public void send (String raw)
  {
    try
    {
      sock.getOutputStream().write(raw.getBytes());
    }
    catch (Exception e)
    {
      println(e);
    }
  }
  
  private void doReconnect ()
  {
    this.sock = null;
    try
    {
      this.sock = new Socket(this.addr, this.port);
    }
    catch (Exception e)
    {
      //println("Connection failed: " + e);
    }
  }
  
  Connection ()
  {
  }
  
  Connection (String addr, int port)
  {
    this(addr, port, false);
  }

  Connection (String addr, int port, boolean reconnect)
  {
    connect(addr, port, reconnect);
  }

  public void connect (String addr, int port, boolean reconnect)
  {
    this.reconnect = reconnect;
    this.addr = addr;
    this.port = port;
    if (reconnect)
    {
      begin(true);
    }
    else
    { 
      doReconnect();
      begin(false);
    }
  }
  
  private void begin (boolean force)
  {
    if (this.sock != null || force)
    {
      Thread t = new Thread(this);
      t.start();
    }
  }
  
  Connection (Socket sock)
  {
    reconnect = false;
    this.sock = sock;
    begin(false);
  }
  
  public void run ()
  {
    while (true)
    {
      if (sock != null)
      {
        try
        {
          BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
          while (true)
          {
            String l = reader.readLine();
            if (l == null)
            {
              println("Socket has no more data");
              sock = null;
              break;
            }
            try
            {
              process(l);
            }
            catch (Exception e)
            {
              println("Bad input: " + l);
              println(e);
            }
          }
        }
        catch (Exception e)
        {
          println("Socket exception: " + e);
        }
      }
      this.sock = null;
      if (this.reconnect)
      {
        try
        {
          this.doReconnect();
        }
        catch (Exception ee)
        {
        }
        if (this.sock == null)
        {
          try
          {
            //println("Waiting to retry...");
            Thread.sleep(2000);
          }
          catch (Exception eee)
          {
          }
        }          
        continue;
      }
      break;
    }
    println("Socket loop end");
  }
}

abstract class Node
{
  Vector2D pos;
  ArrayList<Edge> edges = new ArrayList<Edge>();
  boolean selected = false;
  boolean pinned = false;
  Vector2D pinOffset = new Vector2D();
  String label;
  
  public abstract double getRadius ();
  
  Node ()
  {
    setRandomPos();
  }
  
  Node (Vector2D pos)
  {
    this.pos = pos;
  }

  private void setRandomPos ()
  {
    this.pos = new Vector2D(random(width), random(height));
  }
 
  public boolean isConnectedTo (Node other)
  {
    for (Edge e : edges)
    {
      if (e.getOtherEnd(this) == other) return true;
    }
    return false;
  }

  public void drawPin ()
  {
    strokeWeight(3);
    stroke(0x7fffffff);
    fill(0xFFffFFff);
    pos.trunc().plus(pinOffset).drawCircle(12);

    //noStroke();
    if (pinned) fill(0xff7f7f7f);

    pos.trunc().plus(pinOffset).drawCircle(9);
  }

  public boolean click (Vector2D p)
  {
    
    double d = pos.plus(pinOffset).minus(p).getMagnitude();
    if (d < 12)
    {
      handle_click_pin();
      return true;
    }
    if (hit(p))
    {
      handle_click(p);
      return true;
    }
    return false;
  }
  
  public void handle_click_pin ()
  {
    pinned = !pinned;
  }
  
  public void handle_click (Vector2D p)
  {
    if (!selected)
    {
      if (edges.size() > 0)
      {
        int eNum = (int)random(edges.size());
        Edge e = edges.get(eNum);
        //app.packets.add(new Packet(this, e, 2000));
      }
    }
    
    for (Node n : g.nodes)
    {
      n.selected = false;
    }
    selected = true;
    app.dragged = this;
  }
  
  public void drawLabel (Vector2D labelPos)
  {
    fill(0,0,0,64);
    labelPos.plus(1,1).drawTextCentered(label);
    fill(0,0,0);
    labelPos.drawTextCentered(label);
  }
  
  public abstract double getCharge ();
  public abstract boolean hit (Vector2D p);
  public abstract void draw (Graph g);
  
  public String toString ()
  {
    String s = getClass().getName();
    if (label != null && label.length() > 0) s += " " + label;
    return "<" + s + ">";
  }
  
  public void drawArrow ()
  {
    if (g.a == this)
    {
      double a = -PI/3;
      String l = "A";
      fill(0xffffffff);
      stroke(0xffffffff);
      drawArrow(pos.plus(polar(a, getRadius())), l, a, 0, false);
    }
    if (g.b == this)
    {
      double a = -PI + PI/3;
      String l = "B";
      fill(0);
      stroke(0xffffffff);
      drawArrow(pos.plus(polar(a, getRadius())), l, a, PI, true);
    }
  }
  
  public void drawArrow (Vector2D pos, String label, double angle, float extra, boolean black)
  {

    //double angle = -Math.PI/3;
    double headLength = 12;
    double tailLength = headLength*.5f;
    double headWidth = 15;
    double tailWidth = 7;
    Vector2D tip = pos;//new Vector2D(mouseX, mouseY);
    
    tip = tip.plus(polar(angle, 8 * (1 + sin(extra + (millis()) / 80.0f))));
    
    Vector2D back = tip.plus(polar(angle, headLength));
    Vector2D wing1 = back.plus(polar(angle+HALF_PI, headWidth/2));
    Vector2D wing2 = back.plus(polar(angle-HALF_PI, headWidth/2));

    //tip.drawTriangle(10);

//    tip = tip.trunc();
    
    //tip.drawLineTo(wing1);
    //tip.drawLineTo(wing2);
    //wing2.drawLineTo(wing1);
    
    Vector2D box1 = back.plus(polar(angle+HALF_PI, tailWidth/2));
    Vector2D box2 = back.plus(polar(angle-HALF_PI, tailWidth/2));
    Vector2D box3 = box2.plus(polar(angle, tailLength));
    Vector2D box4 = box1.plus(polar(angle, tailLength));
    //box1.drawLineTo(box2);
    //box2.drawLineTo(box3);
    //box3.drawLineTo(box4);
    //box4.drawLineTo(box1);

    
    //if (black)
    {
      fill(0xff000000);
      stroke(0xff000000);
      strokeWeight(3);
      Vector2D b = new Vector2D(2,1);
      beginShape();
      b.plus(tip).vert();
      b.plus(wing1).vert();
      b.plus(box1).vert();
      b.plus(box4).vert();
      b.plus(box3).vert();
      b.plus(box2).vert();
      b.plus(wing2).vert();
      endShape(CLOSE);
    }
    if (black)
    {
    }
    else
    {
      fill(0xffffffff);
    }
    
    stroke(0xffffffff);
    strokeWeight(2);
    
    beginShape();
    tip.vert();
    wing1.vert();
    box1.vert();
    box4.vert();
    box3.vert();
    box2.vert();
    wing2.vert();
    endShape(CLOSE);
    
tip = tip.trunc();    
    Vector2D textPos = tip.plus(polar(angle, headLength + tailLength + 6));
    textAlign(CENTER);
    textSize(16);
    fill(0xff000000);
    textPos.plus(1,1).drawText(label);
    fill(0xffffffff);
    textPos.drawText(label);
  }
}


class CircleNode extends Node
{
  CircleNode ()
  {
    super();
  }
  
  CircleNode (Vector2D pos)
  {
    super(pos);
  }

  public double getCharge ()
  {
    return 3; //1 + 3* Math.log(max(1,edges.size())); // constant 3 works well too
  }

  public boolean hit (Vector2D p)
  {
    return pos.minus(p).getMagnitude() <= getRadius()/2;
  }

  public double getRadius ()
  {
    return (2 + Math.log(max(1,edges.size()))) * 15;
  }

  public void draw (Graph g)
  {
    double radius = getRadius();
    strokeWeight(7);
    if (selected)
    {
      stroke(128,64,255);
      fill(255,255,255);
      pos.trunc().drawCircle(radius);
    }
    else
    {
      stroke(255,255,255,128);
      noFill();
      radius += 1;
      pos.trunc().drawCircle(radius);
      noStroke();
      fill(255,255,255);
      radius -= 4;
      pos.trunc().drawCircle(radius);
      radius += 3;
    }
    
    pinOffset = new Vector2D(radius/2.5f, radius/2.5f);
    drawPin();
    
    drawLabel(pos.trunc());    
  }
}

class RoundedSquareNode extends Node
{
  RoundedSquareNode ()
  {
    super();
  }
  
  RoundedSquareNode (Vector2D pos)
  {
    super(pos);
  }

  public double getCharge ()
  {
    return 3; //1 + 3* Math.log(max(1,edges.size())); // constant 3 works well too
  }

  public boolean hit (Vector2D p)
  {
    return pos.minus(p).getMagnitude() <= getRadius()/2;
  }

  public double getRadius ()
  {
    return (2 + Math.log(max(1,edges.size()))) * 15;
  }

  public void draw (Graph g)
  {
    final int c = 8;
    double radius = getRadius();
    strokeWeight(7);
    if (selected)
    {
      stroke(128,64,255);
      fill(255,255,255);
      pos.trunc().drawSquare(radius, c);
    }
    else
    {
      stroke(255,255,255,128);
      noFill();
      radius += 1;
      pos.trunc().drawSquare(radius, c);
      noStroke();
      fill(255,255,255);
      radius -= 4;
      pos.trunc().plus(.5f,.5f).drawSquare(radius, c);
      radius += 3;
    }

    pinOffset = new Vector2D(radius/2, radius/2);
    drawPin();
    
    drawLabel(pos.trunc());    
  }
}

class TriangleNode extends Node
{
  TriangleNode ()
  {
    super();
  }
  
  TriangleNode (Vector2D pos)
  {
    super(pos);
  }
  
  public double getCharge ()
  {
    return 3; //1 + 3* Math.log(max(1,edges.size())); // constant 3 works well too
  }

  public boolean hit (Vector2D p)
  {
    return pos.minus(p).getMagnitude() <= getRadius()/2;
  }

  public double getRadius ()
  {
    return (2 + Math.log(max(1,edges.size()))) * 15;
  }

  public void draw (Graph g)
  {
    double radius = getRadius();
    strokeWeight(7);
    if (selected)
    {
      stroke(128,64,255);
      fill(255,255,255);
      pos.trunc().drawTriangle(radius);
    }
    else
    {
      stroke(255,255,255,128);
      noFill();
      radius += 1;
      pos.trunc().drawTriangle(radius);
      noStroke();
      fill(255,255,255);
      radius -= 4;
      pos.trunc().drawTriangle(radius);
      radius += 3;
    }
    
    pinOffset = polar(2.0943951023931953f*1-HALF_PI, radius*.65f);
    drawPin();
    
    drawLabel(pos.trunc());    
  }
}

class Packet
{
  Edge e;
  boolean bToA;
  double duration;
  double startTime;
  int strokeColor = 0xFF000000, fillColor = 0;
  boolean drop;
  Vector2D fall_velocity;
  Vector2D oldpos, lastpos;
  boolean falling;
  
  Packet (Node start, Node end, double duration, boolean drop)
  {
    if (end == null)
    {
      init(start, null, duration, drop);
      return;
    }
    
    for (Edge e : start.edges)
    {
      if ((e.a == start && e.b == end)
       || (e.b == start && e.a == end))
      {
        init(start, e, duration, drop);
        return;
      }
    }
    throw new RuntimeException();
  }
  
  Packet (Node start, Edge edge, double duration, boolean drop)
  {
    init(start, edge, duration, drop);
  }
  
  private void init (Node start, Edge edge, double duration, boolean drop)
  {
    e = edge;
    startTime = millis();
    if (e != null)
      bToA = start == e.b;
    else
    {
      duration = 1; // Already falling
      startTime -= 10;
    }
    this.duration = duration;
    this.drop = drop;
  }
  
  public int fixColor (double alpha, int col)
  {
    int a = (col >> 24) & 0xff;
    a = ((int)(a * alpha)) << 24;
    return (col & 0xffFFff) | a; 
  }
  
  public boolean drawDrop ()
  {
    double p = (millis() - startTime) / duration;
    if (p > 1) return false;
    fall_velocity.y += 1;
    lastpos = lastpos.plus(fall_velocity);
    
    //double alpha = 1-p;
    double alpha = -pow(2.3f,(float)((p-1)*3.3f))+1;
    
    if ((strokeColor & 0xff000000) == 0)
      noStroke();
    else
      stroke(fixColor(alpha, strokeColor));
    if ((fillColor & 0xff000000) == 0)
      noFill();
    else
      fill(fixColor(alpha, fillColor));
    lastpos.drawCircle(10);

    return true;
  }
  
  public boolean draw ()
  {
    if (falling)
    {
      return drawDrop();
    }
    double p = (millis() - startTime) / duration;
    if (p > 1)
    {
      if (drop)
      {
        // Initialize dropping phase
        falling = true;
        startTime = millis();
        this.duration = 500;
        if (lastpos != null && oldpos != null)
          fall_velocity = lastpos.minus(oldpos);
        else
          fall_velocity = new Vector2D();          
        if (lastpos == null) lastpos = new Vector2D(bToA ? e.b.pos : e.a.pos);
        return drawDrop();
      }
      return false;
    }
    
    if (bToA) p = 1-p;
    Vector2D pos = e.a.pos.times(1-p).plus(e.b.pos.times(p));
    if ((strokeColor & 0xff000000) == 0)
      noStroke();
    else
      stroke(strokeColor);
    if ((fillColor & 0xff000000) == 0)
      noFill();
    else
      fill(fillColor);
    pos.drawCircle(10);
    oldpos = lastpos;
    lastpos = pos;
    return true;
  }  
}




class Vector2D
{
  double x, y;

  Vector2D ()
  {
    this(0, 0);
  }
  
  Vector2D (Vector2D v)
  {
    this(v.x,v.y);
  }

  Vector2D (double x, double y)
  {
    this.x = x;
    this.y = y;
  }

  public String toString ()
  {
    return "(" + x + "," + y + ")";
  }
  
  public Vector2D plus (Vector2D v) { return new Vector2D(x + v.x, y + v.y); }
  public Vector2D plus (double xx, double yy) { return new Vector2D(x + xx, y + yy); }
  public Vector2D minus (Vector2D v) { return new Vector2D(x - v.x, y - v.y); }
  public Vector2D minus (double xx, double yy) { return new Vector2D(x - xx, y - yy); }
  public Vector2D times (double n) { return new Vector2D(x * n, y * n); }
  public Vector2D times (double xx, double yy) { return new Vector2D(x * xx, y * yy); }
  public Vector2D dividedBy (double n) { return new Vector2D(x / n, y / n); }
  public Vector2D dividedBy (double xx, double yy) { return new Vector2D(x / xx, y / yy); }

  public double getMagnitude () { return Math.sqrt(x*x + y*y); }
  
  public double getTheta () { return Math.atan2(y, x); }
 
  public Vector2D trunc () { return new Vector2D((int)x, (int)y); }
  
  public void drawCircle (double radius)
  {
    ellipse((float)x, (float)y, (float)radius, (float)radius);
  }
  public void drawTriangle (double radius)
  {
    radius *= .65f;
    Vector2D p1 = this.minus(0,radius);
    Vector2D p2 = polar(2.0943951023931953f*1-PI/2, radius);
    Vector2D p3 = this.plus(p2.times(-1,1));
    p2 = this.plus(p2);
    
    triangle((float)p1.x,(float)p1.y,(float)p2.x,(float)p2.y,(float)p3.x,(float)p3.y);
  }
  public void drawSquare (double extent, double rounding)
  {
    rectMode(CENTER);
    rect((float)x, (float)y, (float)extent, (float)extent, (float)rounding);
  }
  public void drawSquare (double extent)
  {
    drawSquare(extent, 0);
  }
  public void drawLineTo (Vector2D other)
  {
    line((float)x, (float)y, (float)other.x, (float)other.y);
  }
  public void drawText (String message)
  {
    if (message == null) return;
    textSize(16);
    text(message, (float)x, (float)y);
  }
  public void drawTextCentered (String message)
  {
    textAlign(CENTER, CENTER);
    drawText(message);
  }
  public void vert ()
  {
    vertex((float)x, (float)y);
  }
}

public Vector2D polar (double theta, double m)
{
  return new Vector2D(cos((float)theta)*m, sin((float)theta)*m);
}

  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "NetVis" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
