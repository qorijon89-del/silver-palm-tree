import java.nio.file.*;
import tj.builder.v1.ManifestIdentity;
public final class PatchManifestFixture {
 public static void main(String[] args)throws Exception {
  Path input=Paths.get(args[0]);Path output=Paths.get(args[1]);
  Files.write(output,ManifestIdentity.change(Files.readAllBytes(input),
    "com.bayram.pogoda","Обу ҳаво",args[2],Integer.parseInt(args[3])));
 }
}
