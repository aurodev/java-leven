package dc;

import core.buffers.DebugMeshVBO;
import core.buffers.MeshDcVBO;
import core.configs.CW;
import core.kernel.Camera;
import core.kernel.Input;
import core.renderer.RenderInfo;
import core.renderer.Renderer;
import core.scene.GameObject;
import core.utils.Constants;
import dc.entities.DebugDrawBuffer;
import dc.impl.LevenLinearOpenCLOctreeImpl;
import dc.impl.opencl.KernelNames;
import dc.impl.opencl.KernelsHolder;
import dc.impl.opencl.OCLUtils;
import dc.shaders.DcSimpleShader;
import dc.shaders.RenderDebugShader;
import dc.utils.RenderDebugCmdBuffer;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

import static dc.ChunkOctree.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class ChunkOctreeWrapper extends GameObject {
    private final ChunkOctree chunkOctree;
    private final KernelsHolder kernelHolder;
    protected boolean drawSeamBounds = false;
    protected boolean drawNodeBounds = false;

    StringBuilder createMainBuildOptions(){
        StringBuilder buildOptions = new StringBuilder();
        buildOptions.append("-cl-denorms-are-zero ");
        buildOptions.append("-cl-finite-math-only ");
        buildOptions.append("-cl-no-signed-zeros ");
        buildOptions.append("-cl-fast-relaxed-math ");
        buildOptions.append("-Werror ");

        int indexShift = log2(VOXELS_PER_CHUNK) + 1;
        int hermiteIndexSize = VOXELS_PER_CHUNK + 1;
        int fieldSize = hermiteIndexSize + 1;
        int indexMask = (1 << indexShift) - 1;

        buildOptions.append("-DFIELD_DIM=").append(fieldSize).append(" ");
        buildOptions.append("-DFIND_EDGE_INFO_STEPS=" + 16 + " ");
        buildOptions.append("-DFIND_EDGE_INFO_INCREMENT=" + (1.f/16.f) + " ");
        buildOptions.append("-DVOXELS_PER_CHUNK=").append(VOXELS_PER_CHUNK).append(" ");
        buildOptions.append("-DVOXEL_INDEX_SHIFT=").append(indexShift).append(" ");
        buildOptions.append("-DVOXEL_INDEX_MASK=").append(indexMask).append(" ");
        buildOptions.append("-DHERMITE_INDEX_SIZE=").append(hermiteIndexSize).append(" ");
        File file = new File(Paths.get("res/opencl/scan.cl").toUri());
        if(file.exists()){
            buildOptions.append("-I ").append(file.getParent());
        }
        return buildOptions;
    }

    // Uncomment necessary implementation in constructor
    public ChunkOctreeWrapper() {
        //ComputeShaderTest computeShaderTest = new ComputeShaderTest(1);
        //computeShaderTest.render();
        //chunkOctree = new ChunkOctree(new PointerBasedOctreeImpl());
        //chunkOctree = new ChunkOctree(new SimpleLinearOctreeImpl());
        StringBuilder kernelBuildOptions = createMainBuildOptions();
        kernelHolder = new KernelsHolder(OCLUtils.getOpenCLContext());
        kernelHolder.buildKernel(KernelNames.DENSITY, kernelBuildOptions);
        kernelHolder.buildKernel(KernelNames.FIND_DEFAULT_EDGES, kernelBuildOptions);
        kernelHolder.buildKernel(KernelNames.SCAN, null);
        //chunkOctree = new ChunkOctree(new TransitionLinearOctreeImpl(meshGen));
        chunkOctree = new ChunkOctree(new LevenLinearOpenCLOctreeImpl(kernelHolder));
        //chunkOctree = new ChunkOctree(new LevenLinearOctreeImpl());
    }

    public void update() {
        chunkOctree.update(Camera.getInstance(), false);

        if (refreshMesh) {
            renderMesh();
        }
        if (Input.getInstance().isKeyHold(GLFW_KEY_F1)) {
            sleep(200);
            drawWireframe = !drawWireframe;
        }
        if (Input.getInstance().isKeyHold(GLFW_KEY_F2)) {
            sleep(200);
            drawNodeBounds = !drawNodeBounds;
        }
        if (Input.getInstance().isKeyHold(GLFW_KEY_F3)) {
            sleep(200);
            refreshMesh = !refreshMesh;
        }
        if (Input.getInstance().isKeyHold(GLFW_KEY_F4)) {
            sleep(200);
            drawSeamBounds = !drawSeamBounds;
        }
        glPolygonMode(GL_FRONT_AND_BACK, drawWireframe ? GL_LINE : GL_FILL);
        if(!drawWireframe){
            glDisable(GL_CULL_FACE);
        }
    }

    public void cleanUp(){
        kernelHolder.destroyContext();
        chunkOctree.clean();
    }

    private void renderMesh() {
        getComponents().clear();
        RenderDebugCmdBuffer renderCmds = new RenderDebugCmdBuffer();

        List<RenderMesh> renderNodes = chunkOctree.getRenderMeshes(true);

        for (RenderMesh node : renderNodes) {
            renderCmds.addCube(node.size == CLIPMAP_LEAF_SIZE ? Constants.Blue : Constants.Green, 0.2f, node.min, node.size);
            if (node.meshRender == null) {
                Renderer renderer = new Renderer(new MeshDcVBO(node.renderMesh));
                renderer.setRenderInfo(new RenderInfo(new CW(), DcSimpleShader.getInstance()));
                node.meshRender = renderer;
            }
            addComponent("chunks " + node.min, node.meshRender);
            if (node.seamMesh != null) {
                if (node.seamRender == null) {
                    Renderer seamRenderer = new Renderer(new MeshDcVBO(node.seamMesh));
                    seamRenderer.setRenderInfo(new RenderInfo(new CW(), DcSimpleShader.getInstance()));
                    node.seamRender = seamRenderer;
                }
                addComponent("seams " + node.min, node.seamRender);
            }
//          if (drawSeamBounds) {
//              renderDebugVoxelsBounds(node);
//          }
        }

        if(drawNodeBounds) {
            DebugDrawBuffer buf = renderCmds.UpdateDebugDrawBuffer();
            DebugMeshVBO debugMeshBuffer = new DebugMeshVBO();
            debugMeshBuffer.addData(buf);
            Renderer debugRenderer = new Renderer(debugMeshBuffer);
            debugRenderer.setRenderInfo(new RenderInfo(new CW(), RenderDebugShader.getInstance()));
            addComponent(Constants.RENDERER_COMPONENT, debugRenderer);
        }
    }

    private void renderDebugVoxelsBounds(ChunkNode node){
        RenderDebugCmdBuffer renderDebugVoxelsBounds = new RenderDebugCmdBuffer();
        for(PointerBasedOctreeNode n : node.seamNodes){
            renderDebugVoxelsBounds.addCube(Constants.White, 0.2f, n.min, n.size);
        }
        DebugDrawBuffer buf = renderDebugVoxelsBounds.UpdateDebugDrawBuffer();
        DebugMeshVBO debugMeshBuffer = new DebugMeshVBO();
        debugMeshBuffer.addData(buf);
        Renderer debugRenderer = new Renderer(debugMeshBuffer);
        debugRenderer.setRenderInfo(new RenderInfo(new CW(), RenderDebugShader.getInstance()));
        addComponent("voxel nodes " + node.min, debugRenderer);
    }
}
