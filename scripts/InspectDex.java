import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import java.io.File;

/** Offline inspection helper. Requires the pinned JADX distribution on its classpath. */
public class InspectDex {
    static String signature(Method m) {
        return m.getDefiningClass() + "->" + m.getName() + "(" + String.join("", m.getParameterTypes()) + ")" + m.getReturnType();
    }
    public static void main(String[] args) throws Exception {
        var container = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        String mode = args[1], owner = args[2], name = args.length > 3 ? args[3] : "";
        for (String entry : container.getDexEntryNames()) {
            for (ClassDef c : container.getEntry(entry).getDexFile().getClasses()) {
                if (mode.equals("subclasses") && owner.equals(c.getSuperclass())) {
                    System.out.println(entry + " " + c.getType());
                    continue;
                }
                if (mode.equals("types") && c.getType().contains(owner)) {
                    System.out.println(entry + " " + c.getType());
                    continue;
                }
                if (mode.equals("class") && c.getType().equals(owner)) {
                    System.out.println(entry + " CLASS " + c.getType() + " SUPER " + c.getSuperclass() + " INTERFACES " + c.getInterfaces());
                    for (Field f : c.getFields()) System.out.println("FIELD " + f.getName() + " " + f.getType());
                    for (Method m : c.getMethods()) System.out.println("METHOD " + signature(m));
                    continue;
                }
                for (Method m : c.getMethods()) {
                    if (m.getImplementation() == null) continue;
                    boolean dump = mode.equals("code") && c.getType().equals(owner) && m.getName().equals(name);
                    int pc = 0;
                    for (Instruction instruction : m.getImplementation().getInstructions()) {
                        if (instruction instanceof ReferenceInstruction ri) {
                            Reference ref = ri.getReference();
                            boolean match = mode.equals("field") && ref instanceof FieldReference f && (owner.equals("ANY") || f.getDefiningClass().equals(owner)) && f.getName().equals(name);
                            match |= mode.equals("method") && ref instanceof MethodReference mr && (owner.equals("ANY") || mr.getDefiningClass().equals(owner)) && mr.getName().equals(name);
                            if (match || (dump && !(ref instanceof StringReference))) {
                                System.out.println(entry + " " + signature(m) + " @" + pc + " " + instruction.getOpcode() + " " + ref);
                            }
                        }
                        pc += instruction.getCodeUnits();
                    }
                }
            }
        }
    }
}
