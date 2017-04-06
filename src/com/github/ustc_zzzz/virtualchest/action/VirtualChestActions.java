package com.github.ustc_zzzz.virtualchest.action;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.chat.ChatTypes;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.text.title.Title;
import org.spongepowered.api.util.Tuple;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author ustc_zzzz
 */
public final class VirtualChestActions
{
    private static final char SEQUENCE_SPLITTER = ';';
    private static final char PREFIX_SPLITTER = ':';

    private final VirtualChestPlugin plugin;
    private final Map<String, VirtualChestActionExecutor> executors = new HashMap<>();
    private final Map<Player, LinkedList<Tuple<String, String>>> playersInAction = new WeakHashMap<>();
    private final Map<Player, Callback> playerCallbacks = new WeakHashMap<>();

    public VirtualChestActions(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;

        registerPrefix("console", this::processConsole);
        registerPrefix("tell", this::processTell);
        registerPrefix("broadcast", this::processBroadcast);
        registerPrefix("title", this::processTitle);
        registerPrefix("bigtitle", this::processBigtitle);
        registerPrefix("subtitle", this::processSubtitle);
        registerPrefix("delay", this::processDelay);

        registerPrefix("", this::process);

        TitleManager.enable(plugin);
    }

    public void registerPrefix(String prefix, VirtualChestActionExecutor executor)
    {
        this.executors.put(prefix, executor);
    }

    public boolean isPlayerInAction(Player player)
    {
        return this.playersInAction.containsKey(player);
    }

    public void runCommand(Player player, String commandString)
    {
        LinkedList<Tuple<String, String>> commandList = parseCommand(commandString).stream().map(command ->
        {
            int colonPos = command.indexOf(PREFIX_SPLITTER);
            String prefix = colonPos > 0 ? command.substring(0, colonPos) : "";
            if (this.executors.containsKey(prefix))
            {
                int length = command.length(), suffixPosition = colonPos + 1;
                while (suffixPosition < length && Character.isWhitespace(command.charAt(suffixPosition)))
                {
                    ++suffixPosition;
                }
                String suffix = command.substring(suffixPosition);
                return Tuple.of(prefix, this.plugin.getPlaceholderParser().parseAction(player, suffix));
            }
            else
            {
                return Tuple.of("", this.plugin.getPlaceholderParser().parseAction(player, command));
            }
        }).collect(Collectors.toCollection(LinkedList::new));
        this.runCommand(new Callback(player, commandList));
    }

    private void runCommand(Callback callback)
    {
        Sponge.getScheduler().createTaskBuilder().name("VirtualChestItemActionCallback")
                .delayTicks(1).execute(task -> callback.accept(CommandResult.empty())).submit(this.plugin);
    }

    private void process(Player player, String command, Consumer<CommandResult> callback)
    {
        callback.accept(Sponge.getCommandManager().process(player, command));
    }

    private void processDelay(Player player, String command, Consumer<CommandResult> callback)
    {
        try
        {
            int delayTick = Integer.parseInt(command);
            if (delayTick <= 0)
            {
                throw new NumberFormatException();
            }
            Consumer<Task> taskExecutor = task -> callback.accept(CommandResult.success());
            Sponge.getScheduler().createTaskBuilder().delayTicks(delayTick).execute(taskExecutor).submit(this.plugin);
        }
        catch (NumberFormatException e)
        {
            callback.accept(CommandResult.empty());
        }
    }

    private void processBigtitle(Player player, String command, Consumer<CommandResult> callback)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        TitleManager.pushBigtitle(text, player);
        callback.accept(CommandResult.success());
    }

    private void processSubtitle(Player player, String command, Consumer<CommandResult> callback)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        TitleManager.pushSubtitle(text, player);
        callback.accept(CommandResult.success());
    }

    private void processTitle(Player player, String command, Consumer<CommandResult> callback)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        player.sendMessage(ChatTypes.ACTION_BAR, text);
        callback.accept(CommandResult.success());
    }

    private void processBroadcast(Player player, String command, Consumer<CommandResult> callback)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        Sponge.getServer().getBroadcastChannel().send(text);
        callback.accept(CommandResult.success());
    }

    private void processTell(Player player, String command, Consumer<CommandResult> callback)
    {
        player.sendMessage(TextSerializers.FORMATTING_CODE.deserialize(command));
        callback.accept(CommandResult.success());
    }

    private void processConsole(Player player, String command, Consumer<CommandResult> callback)
    {
        callback.accept(Sponge.getCommandManager().process(Sponge.getServer().getConsole(), command));
    }

    private static List<String> parseCommand(String commandSequence)
    {
        StringBuilder stringBuilder = new StringBuilder();
        List<String> commands = new LinkedList<>();
        int i = -1, length = commandSequence.length();
        char c = SEQUENCE_SPLITTER;
        while (i < length)
        {
            if (c != SEQUENCE_SPLITTER)
            {
                stringBuilder.append(c);
                ++i;
            }
            else if (++i < length)
            {
                char next = commandSequence.charAt(i);
                if (next != SEQUENCE_SPLITTER)
                {
                    while (i < length && Character.isWhitespace(commandSequence.charAt(i)))
                    {
                        ++i;
                    }
                    if (stringBuilder.length() > 0)
                    {
                        commands.add(stringBuilder.toString());
                        stringBuilder.setLength(0);
                    }
                }
                else
                {
                    stringBuilder.append(SEQUENCE_SPLITTER);
                    ++i;
                }
            }
            if (i < length)
            {
                c = commandSequence.charAt(i);
            }
        }
        if (stringBuilder.length() > 0)
        {
            commands.add(stringBuilder.toString());
        }
        return commands;
    }

    private class Callback implements Consumer<CommandResult>
    {
        private final WeakReference<Player> player;

        private Callback(Player p, LinkedList<Tuple<String, String>> commandList)
        {
            player = new WeakReference<>(p);
            playersInAction.put(p, commandList);
            playerCallbacks.put(p, this);
        }

        @Override
        public void accept(CommandResult commandResult)
        {
            Optional<Player> playerOptional = Optional.ofNullable(player.get());
            if (playerOptional.isPresent())
            {
                Player p = playerOptional.get();
                LinkedList<Tuple<String, String>> commandList = playersInAction.getOrDefault(p, new LinkedList<>());
                if (commandList.isEmpty())
                {
                    playersInAction.remove(p);
                    playerCallbacks.remove(p);
                }
                else
                {
                    Tuple<String, String> t = commandList.pop();
                    Optional<Callback> callbackOptional = Optional.ofNullable(playerCallbacks.get(p));
                    callbackOptional.ifPresent(c -> executors.get(t.getFirst()).doAction(p, t.getSecond(), c));
                }
            }
        }
    }

    private static class TitleManager
    {
        private static final Map<Player, Text> BIGTITLES = new WeakHashMap<>();
        private static final Map<Player, Text> SUBTITLES = new WeakHashMap<>();

        private static Optional<Task> task = Optional.empty();

        private static void sendTitle(Task task)
        {
            Map<Player, Title.Builder> builderMap = new HashMap<>();
            for (Map.Entry<Player, Text> entry : BIGTITLES.entrySet())
            {
                builderMap.compute(entry.getKey(), (player, builder) ->
                        Optional.ofNullable(builder).orElse(Title.builder()).title(entry.getValue()));
            }
            BIGTITLES.clear();
            for (Map.Entry<Player, Text> entry : SUBTITLES.entrySet())
            {
                builderMap.compute(entry.getKey(), (player, builder) ->
                        Optional.ofNullable(builder).orElse(Title.builder()).subtitle(entry.getValue()));
            }
            SUBTITLES.clear();
            builderMap.forEach((player, builder) -> player.sendTitle(builder.build()));
        }

        public static void pushBigtitle(Text title, Player player)
        {
            BIGTITLES.put(player, title);
        }

        public static void pushSubtitle(Text title, Player player)
        {
            SUBTITLES.put(player, title);
        }

        public static void enable(Object plugin)
        {
            if (!task.isPresent())
            {
                task = Optional.of(Sponge.getScheduler().createTaskBuilder().intervalTicks(1)
                        .name("VirtualChestTitleManager").execute(TitleManager::sendTitle).submit(plugin));
            }
        }
    }
}
