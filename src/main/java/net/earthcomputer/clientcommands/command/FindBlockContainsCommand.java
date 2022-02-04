package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.command.arguments.ClientBlockPredicateArgumentType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientBlockPredicateArgumentType.ClientBlockPredicate;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class FindBlockContainsCommand  {

    public static final int MAX_RADIUS = 384;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cfindblockcontains");

        dispatcher.register(literal("cfindblockcontains")
            .then(argument("block", word())
                .executes(ctx -> findBlock(ctx.getSource(), predicate(getString(ctx, "block")), MAX_RADIUS, RadiusType.CARTESIAN))
                .then(argument("radius", integer(0, MAX_RADIUS))
                    .executes(ctx -> findBlock(ctx.getSource(), predicate(getString(ctx, "block")), getInteger(ctx, "radius"), RadiusType.CARTESIAN))
                    .then(literal("cartesian")
                        .executes(ctx -> findBlock(ctx.getSource(), predicate(getString(ctx, "block")), getInteger(ctx, "radius"), RadiusType.CARTESIAN)))
                    .then(literal("rectangular")
                        .executes(ctx -> findBlock(ctx.getSource(), predicate(getString(ctx, "block")), getInteger(ctx, "radius"), RadiusType.RECTANGULAR)))
					.then(literal("All")
						.executes(ctx -> findBlockGlow(ctx.getSource(), predicate(getString(ctx, "block")), getInteger(ctx, "radius"), RadiusType.CARTESIAN, 150, 0xffffff))
						.then(argument("seconds", integer(0, 3600))
							.executes(ctx -> findBlockGlow(ctx.getSource(), predicate(getString(ctx, "block")), getInteger(ctx, "radius"), RadiusType.CARTESIAN, getInteger(ctx, "seconds"), 0xffffff))
							.then(literal("blue")
								.executes(ctx -> findBlockGlow(ctx.getSource(), predicate(getString(ctx, "block")), getInteger(ctx, "radius"), RadiusType.CARTESIAN, getInteger(ctx, "seconds"), 0x1c46c5)))
							.then(literal("white")
								.executes(ctx -> findBlockGlow(ctx.getSource(), predicate(getString(ctx, "block")), getInteger(ctx, "radius"), RadiusType.CARTESIAN, getInteger(ctx, "seconds"), 0xffffff)))
							.then(literal("red")
								.executes(ctx -> findBlockGlow(ctx.getSource(), predicate(getString(ctx, "block")), getInteger(ctx, "radius"), RadiusType.CARTESIAN, getInteger(ctx, "seconds"), 0xc91818)))
							.then(literal("yellow")
								.executes(ctx -> findBlockGlow(ctx.getSource(), predicate(getString(ctx, "block")), getInteger(ctx, "radius"), RadiusType.CARTESIAN, getInteger(ctx, "seconds"), 0xf4ed2f)))
							.then(literal("green")
								.executes(ctx -> findBlockGlow(ctx.getSource(), predicate(getString(ctx, "block")), getInteger(ctx, "radius"), RadiusType.CARTESIAN, getInteger(ctx, "seconds"), 0x20bb11)))
							.then(literal("black")
								.executes(ctx -> findBlockGlow(ctx.getSource(), predicate(getString(ctx, "block")), getInteger(ctx, "radius"), RadiusType.CARTESIAN, getInteger(ctx, "seconds"), 0x000000)))
						)
					)
                    .then(literal("taxicab")
                        .executes(ctx -> findBlock(ctx.getSource(), predicate(getString(ctx, "block")), getInteger(ctx, "radius"), RadiusType.TAXICAB))))));
    }

    public static int findBlock(ServerCommandSource source, ClientBlockPredicate block, int radius, RadiusType radiusType) {
        List<BlockPos> candidates;
        if (radiusType == RadiusType.TAXICAB) {
            candidates = findBlockCandidatesInTaxicabArea(source, block, radius);
        } else {
            candidates = findBlockCandidatesInSquareArea(source, block, radius, radiusType);
        }

        BlockPos origin = new BlockPos(source.getPosition());
        BlockPos closestBlock = candidates.stream()
                .filter(pos -> radiusType.distanceFunc.applyAsDouble(pos.subtract(origin)) <= radius)
                .min(Comparator.comparingDouble(pos -> radiusType.distanceFunc.applyAsDouble(pos.subtract(origin))))
                .orElse(null);

        if (closestBlock == null) {
            sendError(new TranslatableText("commands.cfindblock.notFound"));
            return 0;
        } else {
            double foundRadius = radiusType.distanceFunc.applyAsDouble(closestBlock.subtract(origin));
            sendFeedback(new TranslatableText("commands.cfindblock.success.left", foundRadius)
                    .append(getLookCoordsTextComponent(closestBlock))
                    .append(" ")
                    .append(getGlowCoordsTextComponent(new TranslatableText("commands.cfindblock.success.glow"), closestBlock))
                    .append(new TranslatableText("commands.cfindblock.success.right", foundRadius)));
            return 1;
        }
    }
	public static int findBlockGlow(ServerCommandSource source, ClientBlockPredicate block, int radius, RadiusType radiusType, int time, int color){
		List<BlockPos> candidates;
		if (radiusType == RadiusType.TAXICAB) {
			candidates = findBlockCandidatesInTaxicabArea(source, block, radius);
		} else {
			candidates = findBlockCandidatesInSquareArea(source, block, radius, radiusType);
		}
		if (candidates.isEmpty()){
			sendError(new TranslatableText("commands.cfindblock.notFound"));
			return 0;
		} else {
			GlowCommand.glowBlocks(source, candidates, time, color);
			return 1;
		}
	}

    private static List<BlockPos> findBlockCandidatesInSquareArea(ServerCommandSource source, ClientBlockPredicate blockMatcher, int radius, RadiusType radiusType) {
        World world = MinecraftClient.getInstance().world;
        assert world != null;
        BlockPos senderPos = new BlockPos(source.getPosition());
        ChunkPos chunkPos = new ChunkPos(senderPos);

        List<BlockPos> blockCandidates = new ArrayList<>();

        // search in each chunk with an increasing radius, until we increase the radius
        // past an already found block
        int chunkRadius = (radius >> 4) + 1;
        for (int r = 0; r < chunkRadius; r++) {
            for (int chunkX = chunkPos.x - r; chunkX <= chunkPos.x + r; chunkX++) {
                for (int chunkZ = chunkPos.z - r; chunkZ <= chunkPos.z
                        + r; chunkZ += chunkX == chunkPos.x - r || chunkX == chunkPos.x + r ? 1 : r + r) {
                    Chunk chunk = world.getChunk(chunkX, chunkZ);
                    if (searchChunkForBlockCandidates(chunk, senderPos.getY(), blockMatcher, blockCandidates)) {
                        // update new, potentially shortened, radius
                        int dx = chunkPos.x - chunkX;
                        int dz = chunkPos.z - chunkZ;
                        int newChunkRadius;
                        if (radiusType == RadiusType.CARTESIAN) {
                            newChunkRadius = MathHelper.ceil(MathHelper.sqrt(dx * dx + dz * dz) + MathHelper.SQUARE_ROOT_OF_TWO);
                        } else {
                            newChunkRadius = Math.max(Math.abs(chunkPos.x - chunkX), Math.abs(chunkPos.z - chunkZ)) + 1;
                        }
                        if (newChunkRadius < chunkRadius) {
                            chunkRadius = newChunkRadius;
                        }
                    }
                }
            }
        }

        return blockCandidates;
    }

    private static List<BlockPos> findBlockCandidatesInTaxicabArea(ServerCommandSource source, ClientBlockPredicate blockMatcher, int radius) {
        World world = MinecraftClient.getInstance().world;
        assert world != null;
        BlockPos senderPos = new BlockPos(source.getPosition());
        ChunkPos chunkPos = new ChunkPos(senderPos);

        List<BlockPos> blockCandidates = new ArrayList<>();

        // search in each chunk with an increasing radius, until we increase the radius
        // past an already found block
        int chunkRadius = (radius >> 4) + 1;
        for (int r = 0; r < chunkRadius; r++) {
            for (int chunkX = chunkPos.x - r; chunkX <= chunkPos.x + r; chunkX++) {
                int chunkZ = chunkPos.z - (r - Math.abs(chunkPos.x - chunkX));
                for (int i = 0; i < 2; i++) {
                    Chunk chunk = world.getChunk(chunkX, chunkZ);
                    if (searchChunkForBlockCandidates(chunk, senderPos.getY(), blockMatcher, blockCandidates)) {
                        // update new, potentially shortened, radius
                        int newChunkRadius = Math.abs(chunkPos.x - chunkX) + Math.abs(chunkPos.z - chunkZ) + 1;
                        if (newChunkRadius < chunkRadius) {
                            chunkRadius = newChunkRadius;
                        }
                    }

                    chunkZ = chunkPos.z + (r - Math.abs(chunkPos.x - chunkX));
                }
            }
        }

        return blockCandidates;
    }

    private static boolean searchChunkForBlockCandidates(Chunk chunk, int senderY, ClientBlockPredicate blockMatcher, List<BlockPos> blockCandidates) {
        ClientWorld world = MinecraftClient.getInstance().world;
        assert world != null;

        int bottomY = world.getBottomY();
        int topY = world.getTopY();

        boolean found = false;
        int maxY = chunk.getHighestNonEmptySectionYOffset() + 15;

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        // search every column for the block
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // search the column nearest to the sender first, and stop if we find the block
                int maxDy = Math.max(senderY - bottomY, maxY - senderY);
                for (int dy = 0; dy <= maxDy; dy = dy > 0 ? -dy : -dy + 1) {
                    if (senderY + dy < bottomY || senderY + dy > topY) {
                        continue;
                    }
                    int worldX = (chunk.getPos().x << 4) + x;
                    int worldZ = (chunk.getPos().z << 4) + z;
                    if (blockMatcher.test(world, mutablePos.set(worldX, senderY + dy, worldZ))) {
                        blockCandidates.add(mutablePos.toImmutable());
                        found = true;
                        break;
                    }
                }
            }
        }

        return found;
    }
	private static ClientBlockPredicateArgumentType.ClientBlockPredicate predicate(String query) {
		return blockPredicateFromLinePredicate(line -> line.contains(query)); //this is lambda
	}
	private static ClientBlockPredicateArgumentType.ClientBlockPredicate blockPredicateFromLinePredicate(Predicate<String> linePredicate) {
		return (blockView, pos) -> linePredicate.test(blockView.getBlockState(pos).getBlock().getTranslationKey());
	}
    public enum RadiusType {
        CARTESIAN(pos -> Math.sqrt(pos.getSquaredDistance(BlockPos.ORIGIN))),
        RECTANGULAR(pos -> Math.max(Math.max(Math.abs(pos.getX()), Math.abs(pos.getY())), Math.abs(pos.getZ()))),
        TAXICAB(pos -> pos.getManhattanDistance(BlockPos.ORIGIN));

        final ToDoubleFunction<BlockPos> distanceFunc;
        RadiusType(ToDoubleFunction<BlockPos> distanceFunc) {
            this.distanceFunc = distanceFunc;
        }
    }

}
