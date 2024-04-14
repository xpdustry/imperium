package imperiumV2.votes;

import arc.Core;
import arc.Events;
import arc.math.geom.Point2;
import arc.struct.Seq;
import arc.util.Timer;
import imperiumV2.MindustryCategory;
import imperiumV2.TimeKeeper;
import imperiumV2.discord.BotJDA;
import imperiumV2.handlers.TieredCommandHandler;
import imperiumV2.handlers.VoteHandler;
import imperiumV2.libs.Emojis;
import imperiumV2.libs.JavaLib;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Iconc;
import mindustry.gen.Player;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.StaticWall;

import java.util.HashMap;

public class Excavate {
    public static final int tileCost = 10;
    private static VoteHandler voteHandler = null;
    private static Team team;
    private static final Seq<Tile> tiles = new Seq<>();
    private static final Seq<Point2> borders = new Seq<>();
    private static Timer.Task label;
    //anti-foo client
    private static long soonestVote;
    private static final HashMap<Player, JavaLib.SelectArea> excavateSelection = new HashMap<>();
    private static final Seq<Seq<Tile>> excavateQueue = new Seq<>();

    private static long lastWarning = System.currentTimeMillis();

    public static void RegisterEvents() {
        Events.on(EventType.ServerLoadEvent.class, ignored -> voteHandler = new VoteHandler.VoteHandlerBuilder("E")
                .setVoteMessage((p, b, vh) -> p.name + (b ? " [lime]wants " : " [scarlet]doesn't want ") + "[white]to excavate area.\nType [lightgray]/e y[gray]/[lightgray]n [white]to vote. " + vh + " more [lightgray]/e y [white]votes required.")
                .setSuccess(vh -> {
                    label.cancel();
                    excavateQueue.add(tiles);
                    vh.call(tiles.size + " tiles will be excavated.");
                })
                .setFailure(vh -> {
                    label.cancel();
                    team.items().add(Items.blastCompound, tiles.size * tileCost);
                    vh.call(tiles.size * tileCost + " blast " + Emojis.getEmoji(Items.blastCompound) + " will be returned to core.");
                })
                .build());
        Events.on(EventType.PlayerLeave.class, event -> excavateSelection.remove(event.player));

        Events.on(EventType.TapEvent.class, event -> {
            JavaLib.SelectArea sa = excavateSelection.get(event.player);
            if (sa != null) {
                if (sa.pos1 == -1) {
                    sa.setPos1(event.tile.pos());
                    event.player.sendMessage("pos 1 set.");
                    Call.label(event.player.con, "Pos 1", 5, event.tile.x * Vars.tilesize, event.tile.y * Vars.tilesize);
                } else if (sa.pos2 == -1) {
                    sa.setPos2(event.tile.pos());
                    event.player.sendMessage("pos 2 set.");
                    Call.label(event.player.con, "Pos 2", 5, event.tile.x * Vars.tilesize, event.tile.y * Vars.tilesize);
                } else {
                    var a = Point2.unpack(sa.pos1);
                    Call.label(event.player.con, "Pos 1", 5, a.x * Vars.tilesize, a.y * Vars.tilesize);
                    var b = Point2.unpack(sa.pos2);
                    Call.label(event.player.con, "Pos 2", 5, b.x * Vars.tilesize, b.y * Vars.tilesize);
                    event.player.sendMessage("Type '/e go' to initialize the excavation vote.");
                }
            }
        });

        Events.run(TimeKeeper.S1, () -> {
            for (var ts : excavateQueue) {
                var borders = new Seq<Tile>();

                for (var t : ts) {
                    var per = getPerimeter(t);
                    if (per != null) {
                        for (Tile p : per) {
                            if (!ts.contains(p) || p.block() == null || p.block().id < 3 || !(p.block() instanceof StaticWall)) {//if perimeter outside boundary or non excavate
                                borders.add(t);
                                break;
                            }
                        }
                    }
                }

                ts.removeAll(borders);

                for (Tile t : borders) {
                    //Call.effect(Fx.flakExplosion, t.x * 8f, t.y * 8f, 0, Color.white);
                    Call.constructFinish(t, Blocks.air, null, (byte) 0, Team.derelict, false);
                }
            }
            JavaLib.removeIf(excavateQueue, Seq::isEmpty);
        });
    }

    public static void RegisterClientCommands(TieredCommandHandler handler, TieredCommandHandler.CommandBuilder builder) {
        handler.register(builder.<Player>setup("e", "[go]", "Vote to excavate tiles.", (args, c, p) -> {
            if (args.length == 0) {
                if (excavateSelection.containsKey(p)) {
                    excavateSelection.remove(p);
                    p.sendMessage("Excavation selection cancelled.");
                } else {
                    excavateSelection.put(p, new JavaLib.SelectArea());
                    p.sendMessage("Excavation selection started.");
                }
            } else {
                if (voteHandler.avail()) {
                    JavaLib.SelectArea sa = excavateSelection.get(p);
                    if (sa == null || sa.pos1 == -1 || sa.pos2 == -1) {
                        p.sendMessage("Finish selecting pos 1 and pos 2 before starting excavation!");
                        return;
                    }
                    var a = Point2.unpack(sa.pos1);
                    var b = Point2.unpack(sa.pos2);
                    int xl = Math.min(a.x, b.x);
                    int yb = Math.min(a.y, b.y);
                    int xr = Math.max(a.x, b.x);
                    int yt = Math.max(a.y, b.y);

                    tiles.clear();
                    borders.clear();
                    for (int x = xl; x <= xr; x++) {
                        for (int y = yb; y <= yt; y++) {
                            Tile t = Vars.world.tile(x, y);
                            if (cantExcavate(t)) continue;
                            tiles.add(t);
                            var d = getPerimeter(t);
                            if (d == null) {
                                if (System.currentTimeMillis() > lastWarning + TimeKeeper.minute) {
                                    BotJDA.log(BotJDA.LogLevel.error, "Tile block " + t.block() + " returned null perimeter!");
                                    lastWarning = System.currentTimeMillis();
                                }
                                continue;
                            }
                            for (Tile per : d) {
                                if ((per.x < xl || per.x > xr || per.y < yb || per.y > yt) || cantExcavate(per)) {//if perimeter outside boundary or non excavatable
                                    borders.add(new Point2().set(x * Vars.tilesize, y * Vars.tilesize));
                                    break;
                                }
                            }
                        }
                    }

                    if (tiles.size == 0) {
                        p.sendMessage("[scarlet]No tiles available for excavation in selected area!");
                        excavateSelection.remove(p);
                        return;
                    }

                    if (!p.team().items().has(Items.blastCompound, tiles.size * tileCost)) {
                        p.sendMessage("Not enough " + Emojis.getEmoji(Items.blastCompound) + " to perform excavation! " + tiles.size * tileCost + Emojis.getEmoji(Items.blastCompound) + " needed.");
                        return;
                    }

                    excavateSelection.remove(p);
                    //setup vote handler variables
                    voteHandler.start();
                    voteHandler.call("Vote started to excavate " + tiles.size + " tiles @ (" + tiles.get(0).x + "," + tiles.get(0).y + "). This will use " + tiles.size * tileCost + Emojis.getEmoji(Items.blastCompound) + " from core.");
                    voteHandler.votesRequired = (short) switch (voteHandler.waitingForVote.size) {
                        case 0 -> 0;
                        case 1 -> 1;
                        case 2, 3, 4, 5 -> 2;
                        case 6, 7, 8, 9 -> 3;
                        default -> 4;
                    };
                    team = p.team();
                    soonestVote = System.currentTimeMillis() + 500L;
                    //run
                    team.items().remove(Items.blastCompound, tiles.size * tileCost);
                    label = Timer.schedule(() -> Core.app.post(() -> {
                        for (Point2 p2 : borders) {
                            Call.label(Emojis.getEmoji(Items.blastCompound), 3.05f, p2.x, p2.y);
                        }
                    }), 0, 3);

                    voteHandler.registerVote(p, true);
                } else if (voteHandler.active) {
                    if (System.currentTimeMillis() < soonestVote) return; //anti-foo client
                    if (voteHandler.waitingForVote.contains(p)) {
                        boolean a = args[0].equalsIgnoreCase("y"), b = args[0].equalsIgnoreCase("n");
                        if (!a && !b) {
                            p.sendMessage("[scarlet]Vote either 'y' (yes) or 'n' (no).");
                        } else {
                            voteHandler.registerVote(p, args[0].equalsIgnoreCase("y"));
                        }
                    } else {
                        p.sendMessage("[scarlet]" + Iconc.warning + " Unable to cast vote! []Either you already voted, were afk or joined after the vote started!");
                    }
                } else {
                    p.sendMessage("[scarlet]" + Iconc.warning + " Unable to start Excavation at this moment.");
                }
            }
        }).setParameters(new TieredCommandHandler.CommandParameter("y/n", "Vote 'y' yes or 'n' no", true, false)).setCategory(MindustryCategory.Votes).build());
    }

    public static boolean cantExcavate(Tile t) {
        return t.block() == null || t.block().id < 3 || !(t.block() instanceof StaticWall) || t.wallDrop() != null;
    }

    private static Seq<Tile> getPerimeter(Tile t) {
        try {
            final Seq<Tile> out = new Seq<>();
            for (int x = -1; x < 2; x++) {
                if (x != 0) {
                    for (int y = -1; y < 2; y++) {
                        if (y != 0) {
                            Tile p = t.nearby(x, y);
                            if (p != null) {
                                out.add(p);
                            }
                        }
                    }
                }
            }
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }
}
