package net.doubledoordev.mtrm.network;

import io.netty.buffer.ByteBuf;
import net.doubledoordev.mtrm.MineTweakerRecipeMaker;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Callable;

/**
 * @author Dries007
 */
public class MessageSend implements IMessage
{
    public boolean remove;
    public boolean shapeless;
    public boolean mirrored;
    public String[] data = new String[10];

    public MessageSend()
    {
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        remove = buf.readBoolean();
        shapeless = buf.readBoolean();
        mirrored = buf.readBoolean();
        data = new String[buf.readInt()];
        for (int i = 0; i < data.length; i++)
        {
            if (buf.readBoolean()) data[i] = ByteBufUtils.readUTF8String(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeBoolean(remove);
        buf.writeBoolean(shapeless);
        buf.writeBoolean(mirrored);
        buf.writeInt(data.length);
        for (String item : data)
        {
            boolean tag = item != null && !item.equals("null");
            buf.writeBoolean(tag);
            if (tag) ByteBufUtils.writeUTF8String(buf, item);
        }
    }

    private String getMarker(boolean noIngredients)
    {
        StringBuilder marker = new StringBuilder(25);
        marker.append("//#MARKER ");
        marker.append(remove ? "REMOVE" : "ADD");
        if (!noIngredients) marker.append(shapeless ? " SHAPELESS" : " SHAPED");
        return marker.toString();
    }

    private String getScript(boolean noIngredients, ArrayList<ArrayList<String>> ingredients)
    {
        StringBuilder script = new StringBuilder(100);
        script.append("recipes.").append(remove ? "remove" : "add");
        if (!noIngredients) script.append(shapeless ? "Shapeless" : "Shaped");
        script.append('(').append(data[0]);
        if (!noIngredients)
        {
            script.append(", ");
            if (shapeless) script.append(ingredients.get(0).toString());
            else script.append(ingredients.toString());
        }
        return script.append(");").toString();
    }

    private MessageResponse makeScript()
    {
        ArrayList<ArrayList<String>> ingredients = new ArrayList<>();
        boolean noIngredients = true;
        if (shapeless)
        {
            ArrayList<String> lst = new ArrayList<>();
            for (int i = 1; i < data.length; i++)
            {
                if (data[i] != null) lst.add(data[i]);
            }
            noIngredients = lst.isEmpty();
            ingredients.add(lst);
        }
        else
        {
            String[][] rawIngredients = new String[][]{new String[3], new String[3], new String[3]};
            boolean rowsNull[] = new boolean[]{true, true, true};
            boolean colsNull[] = new boolean[]{true, true, true};
            for (int i = 1; i < data.length; i++)
            {
                if (data[i] != null)
                {
                    int row = (i - 1) / 3;
                    int col = (i - 1) % 3;
                    noIngredients = false;
                    rowsNull[row] = false;
                    colsNull[col] = false;
                    rawIngredients[row][col] = data[i];
                }
            }
            for (int i = 0; i < rowsNull.length; i++)
            {
                if (!rowsNull[i])
                {
                    ArrayList<String> row = new ArrayList<>();
                    for (int j = 0; j < colsNull.length; j++)
                    {
                        if (!colsNull[j]) row.add(rawIngredients[i][j]);
                    }
                    ingredients.add(row);
                }
            }
        }
        if (data[0] == null) return new MessageResponse(MessageResponse.Status.NO_OUT);
        if (!remove && noIngredients) return new MessageResponse(MessageResponse.Status.NO_IN);
        File oldFile = new File("scripts/MineTweakerRecipeMaker/scripts/", "Crafting.zs");
        File file = new File("scripts", MineTweakerRecipeMaker.NAME + ".zs");
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        try
        {
            if (oldFile.exists()) FileUtils.moveFile(oldFile, file);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        if (!file.exists())
        {
            try
            {
                file.createNewFile();
                PrintWriter printWriter = new PrintWriter(file);
                printWriter.println("// File generated by MineTweakerRecipeMaker");
                printWriter.println("//                     READ THIS HEADER BEFORE EDITING ANYTHING");
                printWriter.println("// ================================================================================");
                printWriter.println("//     This file is read and changed by the mod.");
                printWriter.println("//     If you remove/edit any of the markers, IT WILL STOP WORKING!");
                printWriter.println("//     If you want to make manual edits, make a backup of this file!");
                printWriter.println("//     Markers look like this: \"//#MARKER something\"");
                printWriter.println("//     They indicate where calls should be placed, so that MineTweaker does them in the correct order.");
                printWriter.println("//     Removes come first, then stuff is added.");
                printWriter.println("// ================================================================================");
                printWriter.println("//");
                printWriter.println();
                printWriter.println("// ================================================================================");
                printWriter.println("//#MARKER REMOVE");
                printWriter.println();
                printWriter.println("// ================================================================================");
                printWriter.println("//#MARKER REMOVE SHAPELESS");
                printWriter.println();
                printWriter.println("// ================================================================================");
                printWriter.println("//#MARKER REMOVE SHAPED");
                printWriter.println();
                printWriter.println("// ================================================================================");
                printWriter.println("//#MARKER ADD");
                printWriter.println();
                printWriter.println("// ================================================================================");
                printWriter.println("//#MARKER ADD SHAPELESS");
                printWriter.println();
                printWriter.println("// ================================================================================");
                printWriter.println("//#MARKER ADD SHAPED");
                printWriter.println();
                printWriter.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        try
        {
            final String marker = getMarker(noIngredients);
            List<String> lines = FileUtils.readLines(file);
            ListIterator<String> i = lines.listIterator();
            while (i.hasNext())
            {
                String line = i.next();
                if (line.equals(marker))
                {
                    i.add(getScript(noIngredients, ingredients));
                    break;
                }
            }
            if (!i.hasNext()) return new MessageResponse(MessageResponse.Status.WRITE_ERROR, "Cannot find " + marker + " . Did you edit the file manually?");
            FileUtils.writeLines(file, lines);
            try
            {
                FMLCommonHandler.instance().getMinecraftServerInstance().callFromMainThread(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception
                    {
                        Class.forName("minetweaker.MineTweakerImplementationAPI").getDeclaredMethod("reload").invoke(null);
                        return null;
                    }
                });
            }
            catch (Exception e)
            {
                MineTweakerRecipeMaker.getLogger().info("Auto reload failed. Nothing to be worried about, just annoying.", e);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return new MessageResponse(MessageResponse.Status.WRITE_ERROR, e.toString());
        }
        return new MessageResponse(MessageResponse.Status.OK);
    }

    public static class Handler implements IMessageHandler<MessageSend, IMessage>
    {
        @Override
        public IMessage onMessage(MessageSend message, MessageContext ctx)
        {
            return message.makeScript();
        }
    }
}
