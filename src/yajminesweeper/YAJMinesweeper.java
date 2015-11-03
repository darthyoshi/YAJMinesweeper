/*
 * Y(et) A(nother) (J)ava Minesweeper
 * Copyright (C) 2015  Kay Choi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package yajminesweeper;

import java.awt.CheckboxMenuItem;
import java.awt.MenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.WindowEvent;

import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import processing.core.PApplet;
import processing.core.PImage;
import processing.data.IntList;
import processing.data.JSONObject;

/**
 *
 * @author Kay Choi
 */
public class YAJMinesweeper extends PApplet {
  private static final short gridOffsetX = 12;
  private static final short gridOffsetY = 56;
  private static final short[] gridWidth = {9, 16,  30};
  private static final short[] gridHeight = {9, 16, 16};
  private static final short tileSize = 16;
  private static final short[] numMines = {10, 40, 99};
  private short currentDifficulty = 0;

  private static final short defaultScore = 999;
  private static final String defaultName = "Anonymous";
  private final short[] scores = {defaultScore, defaultScore, defaultScore};
  private final String[] names = {defaultName, defaultName, defaultName};
  private static final String[] difficulties = {"Beginner", "Intermediate", "Expert"};
  private static final String sec = " seconds";

  private long startTime = -1;
  private boolean gameStart = false, gameOver = false, gameWin = false;

  private Canvas canvas;

  private IntList mines, flags;

  private Button heldTile = null;

  private static PImage smilieNormal, smilieAlarm, smilieHeld, smilieWin, smilieLose;

  private static PImage flag, questionMark, mine, mineHit, mineMiss;

  private static PImage tile, tileEmpty;
  private static final PImage[] tileNums = new PImage[8];

  private static PImage counter, counterDash;
  private processing.core.PGraphics counterMines, counterTime, panel;
  private static final PImage[] counterNums = new PImage[10];

  private final java.util.concurrent.Semaphore sem =
    new java.util.concurrent.Semaphore(1, true);

  private final JOptionPane scorePane = new JOptionPane(null,
    JOptionPane.PLAIN_MESSAGE, JOptionPane.YES_NO_OPTION);
  private final static String[] options = {"Reset Scores", "OK"};

  private final JTable table = new JTable();

  @Override
  public void settings() {
    size(164, 208);
    noSmooth();
  }

  @Override
  public void setup() {
    surface.setTitle("YAJMinesweeper");

    smilieNormal = loadImage("img/smilie-normal.png");
    smilieAlarm = loadImage("img/smilie-alarm.png");
    smilieHeld = loadImage("img/smilie-held.png");
    smilieWin = loadImage("img/smilie-win.png");
    smilieLose = loadImage("img/smilie-lose.png");

    flag = loadImage("img/flag.png");
    questionMark = loadImage("img/questionmark.png");
    mine = loadImage("img/mine.png");
    mineHit = loadImage("img/mine-hit.png");
    mineMiss = loadImage("img/mine-miss.png");

    tile = loadImage("img/tile.png");
    tileEmpty = loadImage("img/tile-empty.png");

    counter = loadImage("img/counter.png");
    counterDash = requestImage("img/counter-dash.png");

    short i;
    for(i = 0; i < 10; i++) {
      counterNums[i] = loadImage("img/counter-"+i+".png");
    }

    for(i = 0; i < 8; i++) {
      tileNums[i] = loadImage("img/tile-"+(i+1)+".png");
    }

    canvas = new Canvas(this);
    canvas.resetButton = new Button()
      .setImage(smilieNormal)
      .setSize((short)smilieNormal.width, (short)smilieNormal.height);

    java.awt.MenuBar mbar = new java.awt.MenuBar();

    java.awt.Menu menu = new java.awt.Menu("Game");

    MenuItem newGame = new MenuItem("New");
    MenuItem quit = new MenuItem("Quit");

    menu.add(newGame);
    menu.addSeparator();

    CheckboxMenuItem easy = new CheckboxMenuItem(difficulties[0], true);
    CheckboxMenuItem med = new CheckboxMenuItem(difficulties[1]);
    CheckboxMenuItem hard = new CheckboxMenuItem(difficulties[2]);

    menu.add(easy);
    menu.add(med);
    menu.add(hard);
    menu.addSeparator();

    MenuItem scoreItem = new MenuItem("Best Times...");

    menu.add(scoreItem);
    menu.addSeparator();
    menu.add(quit);

    newGame.addActionListener((ActionEvent e) -> {
      reset();
    });

    easy.addItemListener((ItemEvent e) -> {
      currentDifficulty = 0;
      updateCheckbox(easy, med, hard, currentDifficulty);
      reset();
    });

    med.addItemListener((ItemEvent e) -> {
      currentDifficulty = 1;
      updateCheckbox(easy, med, hard, currentDifficulty);
      reset();
    });

    hard.addItemListener((ItemEvent e) -> {
      currentDifficulty = 2;
      updateCheckbox(easy, med, hard, currentDifficulty);
      reset();
    });

    scoreItem.addActionListener((ActionEvent e) -> {
      this.thread("showScores");
    });

    quit.addActionListener((ActionEvent e) -> {
      exit();
    });

    mbar.add(menu);

    frame = ((processing.awt.PSurfaceAWT.SmoothCanvas)surface.getNative()).getFrame();

    frame.setMenuBar(mbar);

    frame.addWindowListener(new java.awt.event.WindowListener() {
      @Override
      public void windowActivated(WindowEvent arg0) {}

      @Override
      public void windowClosed(WindowEvent arg0) {}

      @Override
      public void windowClosing(WindowEvent arg0) {
        exit();
      }

      @Override
      public void windowDeactivated(WindowEvent arg0) {}

      @Override
      public void windowDeiconified(WindowEvent arg0) {}

      @Override
      public void windowIconified(WindowEvent arg0) {}

      @Override
      public void windowOpened(WindowEvent arg0) {}
    });

    if(new java.io.File("data.json").exists()) {
      try {
        JSONObject jsonSettings = loadJSONObject("data.json");

        try {
          currentDifficulty = (short)jsonSettings.getInt("difficulty", 0);

          updateCheckbox(easy, med, hard, currentDifficulty);

          JSONObject jsonScores = jsonSettings.getJSONObject("scores"), scoreDetails;
          java.util.Iterator<?> scoreIter = jsonScores.keyIterator();
          String scoreKey;
          int index;
          while(scoreIter.hasNext()) {
            scoreKey = (String)scoreIter.next();
            scoreDetails = jsonScores.getJSONObject(scoreKey);
            index = Integer.parseInt(scoreKey);
            names[index] = scoreDetails.getString("name", names[index]);
            scores[index] = (short)scoreDetails.getInt("time", scores[index]);
          }
        } catch(Exception ex) {
          ex.printStackTrace(System.err);
        }
      } catch(RuntimeException e) {
        e.printStackTrace(System.err);
      }
    }

    String blank = "";

    String[] columnNames = {blank, blank, blank};
    Object[][] data = {
      {difficulties[0] + ':', scores[0] + sec, names[0]},
      {difficulties[1] + ':', scores[1] + sec, names[1]},
      {difficulties[2] + ':', scores[2] + sec, names[2]}
    };
    DefaultTableModel model = new DefaultTableModel();
    model.setDataVector(data, columnNames);
    table.setModel(model);
    table.setColumnSelectionAllowed(false);
    table.setRowSelectionAllowed(false);
    table.setCellSelectionEnabled(false);
    table.setFillsViewportHeight(true);
    table.setOpaque(false);
    table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
      @Override
      public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setBorder(noFocusBorder);
        setOpaque(false);
        return this;
      }
    });
    table.setShowGrid(false);

    scorePane.setOptions(options);
    scorePane.setMessage(table);

    init(currentDifficulty);
  }

  /**
   * Updates the menu to reflect the selected difficulty level.
   * @param easy the easy mode menu item to update
   * @param med the normal mode menu item to update
   * @param hard the hard mode menu item to update
   * @param difficulty the selected difficulty
   */
  private void updateCheckbox(
    CheckboxMenuItem easy,
    CheckboxMenuItem med,
    CheckboxMenuItem hard,
    short difficulty
  ) {
    switch(difficulty) {
      case 0:
        easy.setState(true);
        med.setState(false);
        hard.setState(false);
        break;
      case 1:
        easy.setState(false);
        med.setState(true);
        hard.setState(false);
        break;
      case 2:
        easy.setState(false);
        med.setState(false);
        hard.setState(true);
        break;
    }
  }

  @Override
  public void exit() {
    JSONObject jsonSettings = new JSONObject();
    JSONObject jsonScores = new JSONObject();
    JSONObject jsonDetails;

    jsonSettings.setInt("difficulty", currentDifficulty);

    for(int i = 0; i < 3; i++) {
      jsonDetails = new JSONObject();
      jsonDetails.setInt("time", scores[i]);
      jsonDetails.setString("name", names[i]);
      jsonScores.setJSONObject(Integer.toString(i), jsonDetails);
    }

    jsonSettings.setJSONObject("scores", jsonScores);

    saveJSONObject(jsonSettings, "data.json");

    super.exit();
  }

  /**
   * Resets the game.
   */
  private void reset() {
    gameStart = gameOver = gameWin = false;
    startTime = -1;

    init(currentDifficulty);
  }

  /**
   * Initializes the game according to the selected difficulty level.
   * @param difficulty the selected difficulty level
   */
  private void init(int difficulty) {
    try {
      sem.acquire();
    } catch (InterruptedException ex) {
      ex.printStackTrace(System.err);

      Thread.currentThread().interrupt();
    }

    short i, j;

    int newWidth = tileSize*(1+gridWidth[currentDifficulty])+4;
    int newHeight = tileSize*(gridHeight[currentDifficulty])+gridOffsetY+8;

    surface.setSize(newWidth, newHeight);
    canvas.resetButton.setPosition(newWidth/2 - 13 + 2, tileSize);

    panel = createGraphics(newWidth - 14, 37);
    panel.beginDraw();
    panel.noSmooth();
    panel.background(0xffc0c0c0);
    panel.copy(counter, 0, 0, counter.width, counter.height, 7, 6, counter.width, counter.height);
    panel.copy(counter, 0, 0, counter.width, counter.height, panel.width - counter.width - 9, 6, counter.width, counter.height);
    panel.strokeWeight(3);
    panel.stroke(0xffffffff);
    panel.line(2, panel.height-1, panel.width, panel.height-1);
    panel.line(panel.width-1, 2, panel.width-1, panel.height);
    panel.stroke(128);
    panel.line(0, 0, panel.width-2, 0);
    panel.line(0, 0, 0, panel.height-2);
    panel.set(1, panel.height - 2, 0xffc0c0c0);
    panel.set(panel.width - 2, 1, 0xffc0c0c0);
    panel.endDraw();

    canvas.tiles = new Button[gridWidth[difficulty]][gridHeight[difficulty]];

    for(i = 0; i < gridWidth[difficulty]; i++) {
      for(j = 0; j < gridHeight[difficulty]; j++) {
        short yPos = (short)(tileSize*j + gridOffsetY);

        canvas.tiles[i][j] = new Button((short)(tileSize*i + gridOffsetX), yPos)
          .setSize(tileSize, tileSize);
      }
    }

    for(i = 0; i < gridWidth[difficulty]; i++) {
      boolean isTopRow = i == 0;
      boolean isBottomRow = i == gridWidth[difficulty] - 1;

      for(j = 0; j < gridHeight[difficulty]; j++) {
        boolean isLeftCol = j == 0;
        boolean isRightCol = j == gridHeight[difficulty] - 1;

        canvas.tiles[i][j].assignNeighbors(
          !(isTopRow || isLeftCol) ? canvas.tiles[i-1][j-1] : null,
          !isTopRow ? canvas.tiles[i-1][j] : null,
          !(isTopRow || isRightCol) ? canvas.tiles[i-1][j+1] : null,
          !isLeftCol ? canvas.tiles[i][j-1] : null,
          !isRightCol ? canvas.tiles[i][j+1] : null,
          !(isBottomRow || isLeftCol) ? canvas.tiles[i+1][j-1] : null,
          !isBottomRow ? canvas.tiles[i+1][j] : null,
          !(isBottomRow || isRightCol) ? canvas.tiles[i+1][j+1] : null
        );
      }
    }

    mines = new IntList(numMines[difficulty]);
    while(mines.size() < numMines[difficulty]) {
      j = (short)(Math.random()*gridWidth[difficulty]*gridHeight[difficulty]);

      if(!mines.hasValue(j)) {
        mines.append(j);
      }
    }
    mines.sort();

    populateBoard();

    flags = new IntList();

    counterTime = createGraphics(counter.width, counter.height);
    counterTime.beginDraw();
    counterTime.copy(
      counterNums[0],
      0, 0,
      counterNums[0].width, counterNums[0].height,
      2, 2,
      counterNums[0].width, counterNums[0].height
    );
    counterTime.copy(
      counterNums[0],
      0, 0,
      counterNums[0].width, counterNums[0].height,
      counterNums[0].width+4, 2,
      counterNums[0].width, counterNums[0].height
    );
    counterTime.copy(
      counterNums[0],
      0, 0,
      counterNums[0].width, counterNums[0].height,
      2*counterNums[0].width+6, 2,
      counterNums[0].width, counterNums[0].height
    );
    counterTime.endDraw();

    counterMines = createGraphics(counter.width, counter.height);
    counterMines.beginDraw();
    counterMines.copy(
      counterNums[numMines[difficulty]/100],
      0, 0,
      counterNums[0].width, counterNums[0].height,
      2, 2,
      counterNums[0].width, counterNums[0].height
    );
    counterMines.copy(
      counterNums[(numMines[difficulty]%100)/10],
      0, 0,
      counterNums[0].width, counterNums[0].height,
      counterNums[0].width+4, 2,
      counterNums[0].width, counterNums[0].height
    );
    counterMines.copy(
      counterNums[numMines[difficulty]%10],
      0, 0,
      counterNums[0].width, counterNums[0].height,
      2*counterNums[0].width+6, 2,
      counterNums[0].width, counterNums[0].height
    );
    counterMines.endDraw();

    canvas.init();

    sem.release();
  }

  /**
   * Populates the game board with numbers based on the mine distribution.
   */
  private void populateBoard() {
    short i, j, k, l;

    for(Button[] row : canvas.tiles) {
      for(Button col : row) {
        col.setValue((short)0);
        col.setRevealImage(tileEmpty);
      }
    }

    for(i = 0; i < mines.size(); i++) {
      j = (short)mines.get(i);
      l = (short)(j/gridWidth[currentDifficulty]);
      k = (short)(j%gridWidth[currentDifficulty]);

      canvas.tiles[k][l].setValue((short)-1);
      canvas.tiles[k][l].setRevealImage(mine);
    }

    short val;
    for(i = 0; i < gridWidth[currentDifficulty]; i++) {
      for(j = 0; j < gridHeight[currentDifficulty]; j++) {
        boolean isLeftRow = j == 0;
        boolean isRightRow = j == gridHeight[currentDifficulty]-1;

        if(canvas.tiles[i][j].value < 0) {
          if(i > 0) {
            if(!isLeftRow && (val = canvas.tiles[i-1][j-1].value) >= 0) {
              canvas.tiles[i-1][j-1].setValue((short)(val+1));
            }

            if((val = canvas.tiles[i-1][j].value) >= 0) {
              canvas.tiles[i-1][j].setValue((short)(val+1));
            }

            if(!isRightRow &&(val = canvas.tiles[i-1][j+1].value) >= 0) {
              canvas.tiles[i-1][j+1].setValue((short)(val+1));
            }
          }

          if(i < gridWidth[currentDifficulty]-1) {
            if(!isLeftRow && (val = canvas.tiles[i+1][j-1].value) >= 0) {
              canvas.tiles[i+1][j-1].setValue((short)(val+1));
            }

            if((val = canvas.tiles[i+1][j].value) >= 0) {
              canvas.tiles[i+1][j].setValue((short)(val+1));
            }

            if(!isRightRow && (val = canvas.tiles[i+1][j+1].value) >= 0) {
              canvas.tiles[i+1][j+1].setValue((short)(val+1));
            }
          }

          if(!isLeftRow && (val = canvas.tiles[i][j-1].value) >= 0) {
            canvas.tiles[i][j-1].setValue((short)(val+1));
          }

          if(!isRightRow && (val = canvas.tiles[i][j+1].value) >= 0) {
            canvas.tiles[i][j+1].setValue((short)(val+1));
          }
        }
      }
    }
  }

  @Override
  public void mousePressed() {
    short x = (short)((mouseX-gridOffsetX)/tileSize);
    short y = (short)((mouseY-gridOffsetY)/tileSize);

    if(mouseButton == LEFT || mouseButton == CENTER) {
      if(
        mouseButton == LEFT &&
        mouseX >= canvas.resetButton.position[0] &&
        mouseX < canvas.resetButton.position[0] + canvas.resetButton.width &&
        mouseY >= canvas.resetButton.position[1] &&
        mouseY < canvas.resetButton.position[1] + canvas.resetButton.height
      ) {
        heldTile = canvas.resetButton;

        canvas.resetButton.setImage(smilieHeld);
      }

      else {
          canvas.resetButton.setImage(smilieAlarm);
      }

      if(!gameOver && !gameWin) {


        if(
          x >= 0 &&
          x < gridWidth[currentDifficulty] &&
          y >= 0 &&
          y < gridHeight[currentDifficulty]
        ) {
          heldTile = canvas.tiles[x][y];

          if(mouseButton == LEFT || mouseButton == CENTER) {
            if(!heldTile.revealed && heldTile.flagType != 1) {
              heldTile.setImage(tileEmpty);
            }

            if(mouseButton == CENTER) {
              for(Button neighbor : heldTile.neighbors) {
                if(
                  neighbor != null &&
                  !neighbor.revealed &&
                  neighbor.flagType != 1
                ) {
                  neighbor.setImage(tileEmpty);
                }
              }
            }
          }
        }
      }
    }

    else if(
      mouseButton == RIGHT &&
      !gameOver &&
      !gameWin &&
      x >= 0 &&
      x < gridWidth[currentDifficulty] &&
      y >= 0 &&
      y < gridHeight[currentDifficulty]
    ) {
      short index = (short)(x*gridWidth[currentDifficulty]+y);

      canvas.tiles[x][y].cycleFlag();

      if(canvas.tiles[x][y].flagType == 1) {
        flags.append(index);
      }

      else if(flags.hasValue(index)) {
        flags.removeValue(index);
      }

      int mineCount = numMines[currentDifficulty] - flags.size();
      PImage[] mineCounter = new PImage[3];
      if(mineCount < 0) {
        mineCounter[0] = counterDash;

        if(mineCount <= -99) {
          mineCounter[1] = mineCounter[2] = counterNums[9];
        }
        else {
          mineCounter[1] = counterNums[(Math.abs(mineCount)%100)/10];
          mineCounter[2] = counterNums[Math.abs(mineCount)%10];
        }
      }
      else {
        mineCounter[0] = counterNums[mineCount/100];
        mineCounter[1] = counterNums[(mineCount%100)/10];
        mineCounter[2] = counterNums[mineCount%10];
      }
      counterMines = createGraphics(counter.width, counter.height);
      counterMines.beginDraw();
      counterMines.copy(
        mineCounter[0],
        0, 0,
        counterNums[0].width, counterNums[0].height,
        2, 2,
        counterNums[0].width, counterNums[0].height
      );
      counterMines.copy(
        mineCounter[1],
        0, 0,
        counterNums[0].width, counterNums[0].height,
        counterNums[0].width+4, 2,
        counterNums[0].width, counterNums[0].height
      );
      counterMines.copy(
        mineCounter[2],
        0, 0,
        counterNums[0].width, counterNums[0].height,
        2*counterNums[0].width+6, 2,
        counterNums[0].width, counterNums[0].height
      );
      counterMines.endDraw();
    }
  }

  @Override
  public void mouseDragged() {
    if(heldTile == canvas.resetButton && mouseButton == LEFT) {
      if(
        (mouseX < canvas.resetButton.position[0] ||
        mouseX >= canvas.resetButton.position[0] + canvas.resetButton.width||
        mouseY < canvas.resetButton.position[1] ||
        mouseY >= canvas.resetButton.position[1] + canvas.resetButton.height)
      ) {
        canvas.resetButton.setImage(
          gameOver ?
          smilieLose :
          (gameWin ? smilieWin : smilieNormal)
        );
      }
      else {
        canvas.resetButton.setImage(smilieHeld);
      }
    }

    else {
      short x = (short)((mouseX-gridOffsetX)/tileSize);
      short y = (short)((mouseY-gridOffsetY)/tileSize);

      if(heldTile != null && heldTile != canvas.resetButton ) {
        for(Button neighbor : heldTile.neighbors) {
          if(
            neighbor != null &&
            !neighbor.revealed &&
            neighbor.flagType != 1
          ) {
            neighbor.setImage(tile);
          }
        }

        if(!heldTile.revealed && heldTile.flagType != 1) {
          heldTile.setImage(tile);
        }
      }

      if(
        !gameOver &&
        !gameWin &&
        x >= 0 &&
        x < gridWidth[currentDifficulty] &&
        y >= 0 &&
        y < gridHeight[currentDifficulty]
      ) {
        heldTile = canvas.tiles[x][y];

        if(mouseButton == LEFT || mouseButton == CENTER) {
          if(heldTile != null) {
            if(!heldTile.revealed && heldTile.flagType != 1) {
              heldTile.setImage(tileEmpty);
            }

            if(mouseButton == CENTER) {
               for(Button neighbor : heldTile.neighbors) {
                 if(
                   neighbor != null &&
                   !neighbor.revealed &&
                   neighbor.flagType != 1
                 ) {
                   neighbor.setImage(tileEmpty);
                 }
               }
            }
          }
        }
      }
    }
  }

  @Override
  public void mouseReleased() {
    if(heldTile == canvas.resetButton) {
      if(
        mouseX >= canvas.resetButton.position[0] &&
        mouseX < canvas.resetButton.position[0] + canvas.resetButton.width &&
        mouseY >= canvas.resetButton.position[1] &&
        mouseY < canvas.resetButton.position[1] + canvas.resetButton.height
      ) {
        reset();
      }
    }

    else {
      short x = (short)((mouseX-gridOffsetX)/tileSize);
      short y = (short)((mouseY-gridOffsetY)/tileSize);

      if(
        heldTile != null &&
        !heldTile.revealed &&
        heldTile.flagType != 1
      ) {
        heldTile.setImage(tile);
      }

      if(
        x >= 0 &&
        x < gridWidth[currentDifficulty] &&
        y >= 0 &&
        y < gridHeight[currentDifficulty]
      ) {
        if(!gameOver && !gameWin) {
          if(!gameStart) {
            if(mouseButton == LEFT && canvas.tiles[x][y].value == -1) {
              short index = (short)(x+y*gridWidth[currentDifficulty]);
              mines.removeValue(index);

              short i = 0;
              while(mines.hasValue(i)) {
                i++;
              }

              mines.append(i);
              mines.sort();

              populateBoard();
            }

            if(mouseButton != RIGHT) {
              gameStart = true;
              startTime = System.currentTimeMillis();
            }
          }

          if(mouseButton == LEFT) {
            if(canvas.tiles[x][y].value == -1) {
              gameOver(canvas.tiles[x][y]);
            }

            canvas.tiles[x][y].reveal();
          }

          else if(mouseButton == CENTER) {
            short numFlags = 0;

            for(Button neighbor : canvas.tiles[x][y].neighbors) {
              if(
                neighbor != null &&
                !neighbor.revealed &&
                neighbor.flagType != 1
              ) {
                neighbor.setImage(tile);
              }
            }

            if(canvas.tiles[x][y].revealed) {
              for(Button neighbor : canvas.tiles[x][y].neighbors) {
                if(neighbor != null && neighbor.flagType == 1) {
                  numFlags++;
                }
              }

              if(numFlags == canvas.tiles[x][y].value) {
                for(Button neighbor : canvas.tiles[x][y].neighbors) {
                  if(neighbor != null && neighbor.flagType != 1) {
                    if(neighbor.value == -1) {
                      gameOver(neighbor);
                    }

                    neighbor.reveal();
                  }
                }
              }
            }
          }

          boolean win = false;
          winCheck:
          for(Button[] row : canvas.tiles) {
            for(Button col : row) {
              win = (col.value >= 0 && col.revealed) ||
                (!col.revealed && col.value == -1);

              if(!win) {
                break winCheck;
              }
            }
          }

          if(win) {
            gameWin = true;

            short tmpX, tmpY;
            for(int i : mines) {
              tmpX = (short)(i%gridWidth[currentDifficulty]);
              tmpY = (short)(i/gridWidth[currentDifficulty]);

              canvas.tiles[tmpX][tmpY].activateFlag();
            }

            short time = (short)((System.currentTimeMillis() - startTime)/1000);
            if(time < scores[currentDifficulty]) {
              scores[currentDifficulty] = time;
              table.getModel().setValueAt(time+" seconds", currentDifficulty, 1);
              thread("showWinPrompt");
            }
          }
        }
      }
    }

    heldTile = null;

    canvas.resetButton.setImage(
      gameOver ?
      smilieLose :
      (gameWin ? smilieWin :smilieNormal)
    );
  }

  /**
   * Performs the operations associated with losing the game.
   * @param tile the tile triggering the game over
   */
  private void gameOver(Button tile) {
    tile.setRevealImage(mineHit);

    for(int k : mines) {
      canvas.tiles[k%gridWidth[currentDifficulty]][k/gridWidth[currentDifficulty]].reveal();
    }

    for(int l : flags) {
      Button curTile = canvas.tiles[l/gridWidth[currentDifficulty]][l%gridWidth[currentDifficulty]];
      if(curTile.value != -1) {
        curTile.setImage(mineMiss);
      }
    }

    gameOver = true;

    canvas.resetButton.setImage(smilieLose);
  }

  /**
   * Displays the high scores.
   */
  public void showScores() {
    javax.swing.JDialog dialog = new javax.swing.JDialog(frame, "Fastest Mine Sweepers", true);
    dialog.setContentPane(scorePane);
    dialog.setLocationRelativeTo(frame);

    scorePane.addPropertyChangeListener((java.beans.PropertyChangeEvent e) -> {
      String prop = e.getPropertyName();

      if(
        dialog.isVisible() &&
        (e.getSource() == scorePane) &&
        prop.equals(JOptionPane.VALUE_PROPERTY)
      ) {
        if(scorePane.getValue().equals(options[1])) {
          dialog.setVisible(false);
        }

        else if(scorePane.getValue().equals(options[0])) {
          DefaultTableModel model = (DefaultTableModel)table.getModel();

          names[0] = names[1] = names[2] = defaultName;
          scores[0] = scores[1] = scores[2] = defaultScore;

          for(short i = 0; i < 3; i++) {
            model.setValueAt(defaultScore + sec, i, 1);
            model.setValueAt(defaultName, i, 2);
          }
        }
      }
    });

    dialog.pack();
    dialog.setVisible(true);
  }

  /**
   * Displays a prompt for saving record scores.
   */
  public void showWinPrompt() {
    String name = (String)JOptionPane.showInputDialog(
      frame,
      "You have the fastest time for " +
        difficulties[currentDifficulty].toLowerCase() +
        " level.\nPlease enter your name.",
      "Congratulations!",
      JOptionPane.PLAIN_MESSAGE,
      null,
      null,
      null
    );

    if(name != null && name.length() > 0) {
      names[currentDifficulty] = name;
      table.getModel().setValueAt(name, currentDifficulty, 2);
    }

    showScores();
  }

  @Override
  public void draw() {
    background(0xffc0c0c0);
    stroke(0xffffffff);
    strokeWeight(3);
    line(1, 0, 1, height);
    line(0, 1, width, 1);

    line(
      gridOffsetX+1+tileSize*gridWidth[currentDifficulty],
      gridOffsetY-2,
      gridOffsetX+1+tileSize*gridWidth[currentDifficulty],
      gridOffsetY+1+tileSize*gridHeight[currentDifficulty]
    );
    line(
      gridOffsetX-2,
      gridOffsetY+1+tileSize*gridHeight[currentDifficulty],
      gridOffsetX+1+tileSize*gridWidth[currentDifficulty],
      gridOffsetY+1+tileSize*gridHeight[currentDifficulty]
    );
    set(
      gridOffsetX+2+tileSize*gridWidth[currentDifficulty],
      gridOffsetY+2+tileSize*gridHeight[currentDifficulty],
      0xffffffff
    );
    set(
      gridOffsetX+2+tileSize*gridWidth[currentDifficulty],
      gridOffsetY+1+tileSize*gridHeight[currentDifficulty],
      0xffffffff
    );
    set(
      gridOffsetX+1+tileSize*gridWidth[currentDifficulty],
      gridOffsetY+2+tileSize*gridHeight[currentDifficulty],
      0xffffffff
    );

    strokeWeight(1);
    line(0, 3, width, 3);

    stroke(128);
    strokeWeight(3);
    line(
      gridOffsetX-2,
      gridOffsetY-2,
      gridOffsetX+1+tileSize*gridWidth[currentDifficulty],
      gridOffsetY-2
    );
    line(
      gridOffsetX-2,
      gridOffsetY-2,
      gridOffsetX-2,
      gridOffsetY+1+tileSize*gridHeight[currentDifficulty]
    );
    set(
      gridOffsetX-2,
      gridOffsetY+1+tileSize*gridHeight[currentDifficulty],
      0xffc0c0c0
    );
    set(
      gridOffsetX-1,
      gridOffsetY+tileSize*gridHeight[currentDifficulty],
      0xffc0c0c0
    );
    set(
      gridOffsetX+1+tileSize*gridWidth[currentDifficulty],
      gridOffsetY-2,
      0xffc0c0c0
    );
    set(
      gridOffsetX+tileSize*gridWidth[currentDifficulty],
      gridOffsetY-1,
      0xffc0c0c0
    );

    strokeWeight(1);

    if(gameStart && !gameOver && !gameWin) {
      updateTimeCounter();
    }

    try {
      sem.acquire();
    } catch (InterruptedException ex) {
      ex.printStackTrace(System.err);

      Thread.currentThread().interrupt();
    }

    image(panel, 9, 10);
    image(counterMines, 16, 16);
    image(counterTime, width - counterTime.width - tileSize + 2, 16);

    canvas.draw();

    sem.release();
  }

  /**
   * Updates the timer display.
   */
  private void updateTimeCounter() {
    int time = (int)((System.currentTimeMillis() - startTime)/1000);
    counterTime = createGraphics(counter.width, counter.height);
    counterTime.beginDraw();
    counterTime.copy(
      counterNums[time/100],
      0, 0,
      counterNums[0].width, counterNums[0].height,
      2, 2,
      counterNums[0].width, counterNums[0].height
    );
    counterTime.copy(
      counterNums[(time%100)/10],
      0, 0,
      counterNums[0].width, counterNums[0].height,
      counterNums[0].width+4, 2,
      counterNums[0].width, counterNums[0].height
    );
    counterTime.copy(
      counterNums[time%10],
      0, 0,
      counterNums[0].width, counterNums[0].height,
      2*counterNums[0].width+6, 2,
      counterNums[0].width, counterNums[0].height
    );
    counterTime.endDraw();
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    PApplet.main(new String[] { yajminesweeper.YAJMinesweeper.class.getName() });
  }

  private class Button {
    short flagType = 0;
    Button[] neighbors;
    boolean revealed = false;
    PImage revealTile = null, defaultImg = null;
    int[] position = new int[2];
    short value, width, height;

    /**
     * Class constructor.
     */
    Button() {
      this((short)0, (short)0);
    }

    /**
     * Class constructor.
     * @param xPos the x-coordinate of the top left corner
     * @param yPos the y-coordinate of the top left corner
     */
    Button(short xPos, short yPos) {
      this.setPosition(xPos, yPos)
        .setSize(tileSize, tileSize)
        .setImage(tile);
      value = 0;
    }

    /**
     * @param width
     * @param height
     * @return this
     */
    final Button setSize(short width, short height) {
      this.width = width;
      this.height = height;
      return this;
    }

    /**
     * @param xPos
     * @param yPos
     * @return this
     */
    final Button setPosition(int xPos, int yPos) {
      position[0] = xPos;
      position[1] = yPos;
      return this;
    }

    /**
     * @param val
     * @return this
     */
    final Button setValue(short val) {
      value = val;
      return this;
    }

    /**
     * @param img
     * @return this
     */
    final Button setImage(PImage img) {
      defaultImg = img;
      return this;
    }

    /**
     * Sets the neighbors of the Button.
     * @param ul upper left
     * @param u above
     * @param ur upper right
     * @param l left
     * @param r right
     * @param dl lower left
     * @param d below
     * @param dr lower right
     */
    void assignNeighbors(
      Button ul, Button u, Button ur,
      Button l, Button r,
      Button dl, Button d, Button dr
    ) {
      neighbors = new Button[8];
      neighbors[0] = ul;
      neighbors[1] = u;
      neighbors[2] = ur;
      neighbors[3] = l;
      neighbors[4] = r;
      neighbors[5] = dl;
      neighbors[6] = d;
      neighbors[7] = dr;
    }

    /**
     * Cycles through the tile flag states.
     */
    void cycleFlag() {
      flagType = (short)((++flagType)%3);

      switch(flagType) {
        case 0:
          setImage(tile);
          break;
        case 1:
          setImage(flag);
          break;
        case 2:
          setImage(questionMark);
          break;
      }
    }

    /**
     * Reveals the tile.
     */
    void reveal() {
      if(!revealed && flagType != 1) {
        revealed = true;

        if(value == 0) {
          setImage(revealTile);
          for(Button neighbor : neighbors) {
            if(neighbor != null && !neighbor.revealed) {
              neighbor.reveal();
            }
          }
        }

        else if(value > 0) {
          setImage(tileNums[value-1]);
        }

        else if(flagType != 1) {
          setImage(revealTile == null ? mine : revealTile);
        }
      }
    }

    /**
     * Sets the image to show when the Button is revealed.
     * @param img the new image
     * @return this
     */
    Button setRevealImage(PImage img) {
      revealTile = img;
      return this;
    }

    /**
     * Activates the tile flag.
     */
    void activateFlag() {
      flagType = 1;
      setImage(flag);
    }
  }

  private class Canvas {
    PApplet parent = null;
    processing.core.PGraphics canvas = null;
    Button resetButton = null;
    Button[][] tiles = null;

    /**
     * Class constructor.
     * @param parent the instantiating PApplet
     */
    Canvas(PApplet parent) {
      this.parent = parent;
    }

    /**
     * Initializes the game canvas.
     */
    void init() {
      canvas = parent.createGraphics(parent.width, parent.height);
      update();
    }

    /**
     * Draws the game canvas.
     */
    void draw() {
      update();
      parent.image(canvas, 0, 0);
    }

    /**
     * Updates the elements on the game canvas.
     */
    void update() {
      canvas.beginDraw();

      canvas.copy(
        resetButton.defaultImg,
        0, 0,
        resetButton.width, resetButton.height,
        resetButton.position[0], resetButton.position[1],
        resetButton.width, resetButton.height
      );

      for(Button[] col : tiles) {
        for(Button row : col) {
          canvas.copy(
            row.defaultImg,
            0, 0,
            row.width, row.height,
            row.position[0], row.position[1],
            row.width, row.height
          );
        }
      }

      canvas.endDraw();
    }
  }
}
